/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * Unless otherwise indicated, all code in ModeShape is licensed
 * to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.modeshape.connector.cmis;

import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.junit.*;

import org.modeshape.jcr.*;

/**
 * @author: valeriy.shtanko
 * created: 2013-12-06 18:39
 */
@Ignore
public class CmisConnectorConfigurationTestIT extends MultiUseAbstractTest {
    @BeforeClass
    public static void beforeAll() throws Exception {
        startRepository();
    }

    @AfterClass
    public static void afterAll() throws Exception {
        try {
            stopRepository();
        }
        catch(Exception e) {}
    }

    //-------------------------------------------------------------------------
    // TESTS
    //
    @Test ( expected = CmisObjectNotFoundException.class)
    public void shouldFailWhenRemoteFolderNotExists() throws Exception {
        RepositoryConfiguration config = RepositoryConfiguration.read("config/repository-no-remote-folder.json");

        super.startRepository(config);
    }
}
