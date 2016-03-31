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

(ns org.domaindrivenarchitecture.pallet.crate.liferay-test
  (:require
    [clojure.test :refer :all]    
    [clojure.set :as cloj-set]
    [schema.core :as s]
    [schema.experimental.complete :as c]
    [org.domaindrivenarchitecture.pallet.crate.liferay :as sut]
    [org.domaindrivenarchitecture.pallet.crate.liferay.schema :as schema]
    ))

(def release-definition
  (c/complete {:app ["name" "download-url"]} schema/LiferayRelease))

(def db-definition
  (c/complete {} schema/DbConfig))
 
 (deftest defaults
  (testing 
    "test the default release definition" 
      (is (s/validate
            schema/LiferayRelease
            (sut/default-release db-definition)))
      (is (s/validate
            schema/LiferayRelease
           release-definition))
      (is (s/validate
            schema/LiferayReleaseConfig
            {:release-dir "/prepare-rollout/"
             :releases [release-definition]}))
      ))
 
 (deftest config-validation
  (testing 
    "test wheter merged config are validated" 
      (is (thrown? clojure.lang.ExceptionInfo
                   (let [config (sut/merge-config {:an-unexpected-key nil})])))
      (is (map? 
            (sut/merge-config {:third-party-download-root-dir "download root"
                               :httpd {:letsencrypt false
                                       :fqdn "fqdn"
                                       :domain-cert "cert"
                                       :domain-key "key"}})))
      (is (map? 
            (sut/merge-config {:third-party-download-root-dir "download root"
                               :httpd {:letsencrypt false
                                       :fqdn "fqdn"
                                       :domain-cert "cert"
                                       :domain-key "key"}
                               :tomcat {:Xmx "123"}})))
      ))