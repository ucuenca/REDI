/*
 * Copyright 2018 Xavier Sumba <xavier.sumba93@ucuenca.ec>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.marmotta.ucuenca.wk.pubman.api;

/**
 *
 * @author Xavier Sumba <xavier.sumba93@ucuenca.ec>
 */
public interface Centralize {

    /**
     * Names of graphs to be copied in the centralization process (e.g.
     * provider/ScopusProvider, or authors).
     */
    public static final String[] GRAPH_NAMES = {"clusters", "organization", "redi"};

    void resetCopy(String endpoint) throws Exception;

    void copy(String endpoint) throws Exception;
}
