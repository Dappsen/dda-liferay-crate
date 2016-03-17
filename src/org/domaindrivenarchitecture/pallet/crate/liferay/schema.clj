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

(ns org.domaindrivenarchitecture.pallet.crate.liferay.schema
   (:require [clojure.string :as string]
             [schema.core :as s :include-macros true]
             [org.domaindrivenarchitecture.pallet.crate.base.schema :as base]))

(s/defn non-root-directory? 
  "Predicate for directory path not empty und not the unix root."
  [dir :- s/Str]
  (and 
    (not (string/blank? dir))
    (< 1 (.length dir))
    (.endsWith dir "/")
    ))

(def NonRootDirectory
  "Represents a directory with trailing /"
  (s/constrained s/Str non-root-directory?))

(def LiferayApp
  "Represents a liferay application (portlet, theme or the portal itself)."
  [(s/one s/Str "name") (s/one s/Str "url")])

(def LiferayRelease
  "LiferayRelease relates a release name with specification of versioned apps."
  {:name s/Str
   :version base/Version
   :application LiferayApp
   (s/optional-key :config) [s/Str]
   :hooks [LiferayApp]
   :layouts [LiferayApp]
   :themes [LiferayApp]
   :portlets [LiferayApp]
   })

(def LiferayReleaseConfig
  "The configuration for liferay release feature."
  {:release-dir NonRootDirectory
   :releases [LiferayRelease]})