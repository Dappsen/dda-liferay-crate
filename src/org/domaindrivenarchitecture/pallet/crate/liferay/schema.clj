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
   (:require [schema.core :as s :include-macros true]))

(def Version
  "A schema for a nested data type"
  [(s/one s/Num "major") (s/one s/Num "minor") (s/one s/Num "patch")])

(def LiferayApp
  "A schema for a nested data type"
  [(s/one s/Str "name") (s/one s/Str "url")])

(def LiferayRelease
  "LiferayRelease relates a release name with specification of versioned apps."
  {:name s/Str
   :version Version
   :application LiferayApp
   :hooks [LiferayApp]
   :layouts [LiferayApp]
   :themes [LiferayApp]
   :portlets [LiferayApp]})

(def LiferayReleaseConfig
  "The configuration for liferay release feature."
  {:release-dir s/Str
   :releases [LiferayRelease]})