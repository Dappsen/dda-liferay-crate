; Licensed to the Apache Software Foundation (ASF) under one
; or more contributor license agreements. See the NOTICE file
; distributed with this work for additional information
; regarding copyright ownership. The ASF licenses this file
; to you under the Apache License, Version 2.0 (the
; "License"); you may not use this file except in compliance
; with the License. You may obtain a copy of the License at
;
; http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.domaindrivenarchitecture.pallet.crate.liferay.app
   (:require
     [clojure.string :as string]
     [schema.core :as s]
     [schema-tools.core :as st]
     [pallet.actions :as actions]
     [org.domaindrivenarchitecture.pallet.crate.liferay.schema :as schema]
     [org.domaindrivenarchitecture.pallet.crate.liferay.app-config :as app-config]
     [org.domaindrivenarchitecture.pallet.crate.liferay.db-replace-scripts :as db-replace-scripts]
     [pallet.stevedore :as stevedore]
     [pallet.script.scriptlib :as lib]
     [pallet.stevedore.bash :as bash]))

(defn- liferay-config-file
  "Create and upload a config file"
  [file-name content  & {:keys [owner mode]
            :or {owner "tomcat7" mode "644"}}]
  (actions/remote-file
    file-name
    :owner owner
    :group owner
    :mode mode
    :literal true
    :content (string/join \newline content))
  )

(defn- liferay-remote-file
  "Create and upload a config file"
  [file-name url & {:keys [owner mode]
                    :or {owner "tomcat7" mode "644"}}]
  (actions/remote-file
    file-name
    :owner owner
    :group owner
    :mode mode
    :insecure true
    :literal true
    :url url)
  )

(defn- liferay-dir
  "Create and upload a config file"
  [dir-path & {:keys [owner mode]
            :or {owner "tomcat7"
                 mode "755"}}]
  (actions/directory 
      dir-path
      :action :create
      :recursive true
      :owner owner
      :group owner
      :mode mode)
  )

(defn create-liferay-directories
  [liferay-home-dir liferay-lib-dir liferay-release-dir liferay-deploy-dir]
  (liferay-dir (str liferay-home-dir "logs"))
  (liferay-dir (str liferay-home-dir "data"))
  (liferay-dir liferay-deploy-dir)
  (liferay-dir liferay-lib-dir)
  (liferay-dir liferay-release-dir :owner "root")
  )

; TODO: review mje 18.08: Das ist tomcat spezifisch und gehört hier raus.
(defn delete-tomcat-default-ROOT
  [tomcat-root-dir]
  (actions/directory
    tomcat-root-dir
    :action :delete
    :recursive true)
  )

(defn liferay-portal-into-tomcat
  "make liferay tomcat's ROOT webapp"
  [tomcat-root-dir liferay-download-source]
  (actions/remote-directory 
    tomcat-root-dir
    :url liferay-download-source
    :unpack :unzip
    :recursive true
    :owner "tomcat7"
    :group "tomcat7")
  )

(defn liferay-dependencies-into-tomcat
  [liferay-lib-dir repo-download-source]
  "get dependency files" 
  (doseq [jar ["activation" "ccpp" "hsql" "jms" 
               "jta" "jtds" "junit" "jutf7" "mail" 
               "mysql" "persistence" "portal-service" 
               "portlet" "postgresql" "support-tomcat"]]
    (let [download-location (str repo-download-source jar ".jar")
          target-file (str liferay-lib-dir jar ".jar")]
      (liferay-remote-file target-file download-location)))
  )

(s/defn ^:allwas-validate release-name :- s/Str
  "get the release dir name"
  [release :- schema/LiferayRelease ]
  (str (st/get-in release [:name]) "-" (string/join "." (st/get-in release [:version]))))

(s/defn ^:allwas-validate release-dir :- s/Str
  "get the release dir name"
  [base-release-dir :- s/Str 
   release :- schema/LiferayRelease ]
  (str base-release-dir (release-name release)))

(s/defn ^:always-validate download-and-store-applications :- s/Any
  "download and store liferay applications in given directory"
  [dir :- s/Str 
   apps :- [schema/LiferayApp]]
  (liferay-dir dir :owner "root")
  (doseq [app apps]
    (liferay-remote-file (str dir (first app) ".war") (second app) :owner "root")
    )
  )

(s/defn ^:always-validate install-do-rollout-script
  "Creates script for rolling liferay version. To be called by the admin connected to the server via ssh"
  [liferay-home :- schema/NonRootDirectory
   prepare-dir :- schema/NonRootDirectory 
   deploy-dir :- schema/NonRootDirectory
   tomcat-dir :- schema/NonRootDirectory]
  (actions/remote-file
    (str liferay-home "do-rollout.sh")
    :owner "root"
    :group "root"
    :mode "0744"
    :literal true
    :content (app-config/do-deploy-script prepare-dir deploy-dir tomcat-dir)
    ))

(s/defn ^:allwas-validate remove-all-but-specified-versions
  "Removes all other Versions except the specifided Versions"
  [releases :- [schema/LiferayRelease] 
   release-dir :- schema/NonRootDirectory]
  (let [versions (map (str (do (st/get-in releases [:name]) (string/join "." (st/get-in releases [:version])))) releases)]
    (stevedore/with-script-language :pallet.stevedore.bash/bash
      (stevedore/with-source-line-comments false 
        (stevedore/script 
          (pipe (pipe ("ls" ~release-dir) ("grep -Ev" ~versions)) ("xargs -I {} rm -r" ~release-dir "{}"))))
    )
  )
)

(s/defn ^:always-validate prepare-rollout 
  "prepare the rollout of all releases"
  [db-config :- schema/DbConfig
   release-config :- schema/LiferayReleaseConfig]
  (let [base-release-dir (st/get-in release-config [:release-dir])
        releases (st/get-in release-config [:releases])]
    (do (let [release-dir (st/get-in release-config [:release-dir])]
          (actions/exec-script (remove-all-but-specified-versions releases release-dir)))
        (doseq [release releases]
          (let [release-dir (release-dir base-release-dir release)]
            (liferay-dir release-dir :owner "root")
            (download-and-store-applications (str release-dir "/app/") [(st/get-in release [:application])])
            (download-and-store-applications (str release-dir "/hooks/") (st/get-in release [:hooks]))
            (download-and-store-applications (str release-dir "/layouts/") (st/get-in release [:layouts]))
            (download-and-store-applications (str release-dir "/themes/") (st/get-in release [:themes]))
            (download-and-store-applications (str release-dir "/portlets/") (st/get-in release [:portlets]))
            (liferay-dir (str release-dir "/config/") :owner "root")
            (if (contains? release :config)
              (liferay-config-file (str release-dir "/config/portal-ext.properties" (st/get-in release [:config])))
              (liferay-config-file 
                (str release-dir "/config/portal-ext.properties")
                (app-config/var-lib-tomcat7-webapps-ROOT-WEB-INF-classes-portal-ext-properties db-config)
                ))
          ))
    )))



(s/defn install-liferay
  [tomcat-root-dir tomcat-webapps-dir liferay-home-dir 
   liferay-lib-dir liferay-deploy-dir repo-download-source 
   liferay-release-config :- schema/LiferayReleaseConfig]
  "creates liferay directories, copies liferay webapp into tomcat and loads dependencies into tomcat"
  (create-liferay-directories liferay-home-dir liferay-lib-dir (st/get-in liferay-release-config [:release-dir]) liferay-deploy-dir)
  (liferay-dependencies-into-tomcat liferay-lib-dir repo-download-source)
  (liferay-dependencies-into-tomcat liferay-lib-dir repo-download-source)
  (install-do-rollout-script liferay-home-dir (st/get-in liferay-release-config [:release-dir]) liferay-deploy-dir tomcat-webapps-dir)
  )

(defn configure-liferay
  [custom-build? & {:keys [db-name db-user-name db-user-passwd
                    portal-ext-properties fqdn-to-be-replaced fqdn-replacement]}]
  (let [effective-portal-ext-properties 
        (if (empty? portal-ext-properties) 
          (app-config/var-lib-tomcat7-webapps-ROOT-WEB-INF-classes-portal-ext-properties 
            :db-name db-name
            :db-user-name db-user-name
            :db-user-passwd db-user-passwd)
           portal-ext-properties)]
    
    (liferay-config-file
      (if custom-build?
        "/var/lib/liferay/portal-ext.properties"
        "/var/lib/tomcat7/webapps/ROOT/WEB-INF/classes/portal-ext.properties")
       effective-portal-ext-properties)
    
    (liferay-config-file 
      "/var/lib/liferay/prodDataReplacements.sh"
      (db-replace-scripts/var-lib-liferay-prodDataReplacements-sh
        fqdn-to-be-replaced fqdn-replacement db-name db-user-name db-user-passwd)
      :owner "root" :mode "744"))
  )