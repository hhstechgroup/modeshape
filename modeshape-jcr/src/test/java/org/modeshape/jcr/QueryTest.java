/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
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
package org.modeshape.jcr;

import java.util.HashSet;
import java.util.Set;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Value;
import javax.jcr.query.QueryResult;
import org.junit.Assert;
import org.junit.Test;

/**
 * A test case for more complicated query tests that require a fair amount of unique setup.
 */
public class QueryTest extends SingleUseAbstractTest {

    @Test
    public void shouldExecuteThreeWayJoinToResolveRelationships() throws Exception {
        // Register some namespaces ...
        NamespaceRegistry namespaceRegistry = session.getWorkspace().getNamespaceRegistry();
        namespaceRegistry.registerNamespace("sramp", "http://s-ramp.org/xmlns/2010/s-ramp");

        // and node types ...
        registerNodeTypes("cnd/sramp-notional.cnd");

        // Add some artifact nodes.
        session = repository.login();
        Node rootNode = session.getRootNode();

        String jcrNodeType = "sramp:artifact";

        // Node - Artifact A
        Node artifactA = rootNode.addNode("artifact-a", jcrNodeType);
        artifactA.setProperty("sramp:uuid", "1");
        artifactA.setProperty("sramp:name", "A");
        artifactA.setProperty("sramp:model", "core");
        artifactA.setProperty("sramp:type", "Document");

        // Node - Artifact B
        Node artifactB = rootNode.addNode("artifact-b", jcrNodeType);
        artifactB.setProperty("sramp:uuid", "2");
        artifactB.setProperty("sramp:name", "B");
        artifactB.setProperty("sramp:model", "core");
        artifactB.setProperty("sramp:type", "Document");

        // Node - Artifact C
        Node artifactC = rootNode.addNode("artifact-c", jcrNodeType);
        artifactC.setProperty("sramp:uuid", "3");
        artifactC.setProperty("sramp:name", "C");
        artifactC.setProperty("sramp:model", "core");
        artifactC.setProperty("sramp:type", "Document");
        session.save();

        // ////////////////////////////////////////////////////////////////////
        // Add the relationship nodes. Here's what
        // I'm going for here:
        // A has relationships to both B and C of type 'relatesTo'
        // A has a relationship to C of type 'isDocumentedBy'
        // B has a single relationship to C of type 'covets'
        // C has no relationships
        // ////////////////////////////////////////////////////////////////////
        Node relA_relatesTo = artifactA.addNode("relatesTo", "sramp:relationship");
        relA_relatesTo.setProperty("sramp:type", "relatesTo");
        Value[] targets = new Value[2];
        targets[0] = session.getValueFactory().createValue(artifactB, false);
        targets[1] = session.getValueFactory().createValue(artifactC, false);
        relA_relatesTo.setProperty("sramp:target", targets);

        Node relA_isDocumentedBy = artifactA.addNode("isDocumentedBy", "sramp:relationship");
        relA_isDocumentedBy.setProperty("sramp:type", "isDocumentedBy");
        relA_isDocumentedBy.setProperty("sramp:target", session.getValueFactory().createValue(artifactC, false));

        Node relB_covets = artifactB.addNode("relationship-b-1", "sramp:relationship");
        relB_covets.setProperty("sramp:type", "covets");
        relB_covets.setProperty("sramp:target", session.getValueFactory().createValue(artifactC, false));

        session.save();

        // print = true;
        if (print) {
            tools.printSubgraph(artifactA);
            tools.printSubgraph(artifactB);
            tools.printSubgraph(artifactC);
        }
        session.logout();

        String query = null;
        QueryResult jcrQueryResult = null;
        NodeIterator jcrNodes = null;
        Set<String> jcr_uuids = new HashSet<String>();

        // Now it's time to do some querying.
        session = repository.login();

        // Show that we have the 'relatesTo' relationship set up for Artifact A (with two targets)
        query = "SELECT relationship.[sramp:target] AS target_jcr_uuid FROM [sramp:artifact] AS artifact"
                + " JOIN [sramp:relationship] AS relationship ON ISCHILDNODE(relationship, artifact)"
                + " WHERE artifact.[sramp:name] = 'A' AND relationship.[sramp:type] = 'relatesTo'";
        jcrQueryResult = assertJcrSql2Query(query, 1);
        jcrNodes = jcrQueryResult.getNodes();
        Node n = jcrNodes.nextNode();
        String setCriteria = "";
        for (Value value : n.getProperty("sramp:target").getValues()) {
            printMessage("  Found JCR UUID: " + value.getString());
            if (!jcr_uuids.isEmpty()) setCriteria = setCriteria + ", ";
            jcr_uuids.add(value.getString());
            setCriteria = setCriteria + "'" + value.getString() + "'";
        }

        // Now show that the UUIDs found above match the jcr:uuid for Artifact B and Artifact C
        query = "SELECT artifact.[jcr:uuid] FROM [sramp:artifact] AS artifact"
                + " WHERE artifact.[sramp:name] = 'B' OR artifact.[sramp:name] = 'C'";
        jcrQueryResult = assertJcrSql2Query(query, 2);
        jcrNodes = jcrQueryResult.getNodes();
        Node n1 = jcrNodes.nextNode();
        Node n2 = jcrNodes.nextNode();
        Assert.assertTrue("Expected to find the JCR UUID in jcr_uuids",
                          jcr_uuids.contains(n1.getProperty("jcr:uuid").getString()));
        Assert.assertTrue("Expected to find the JCR UUID in jcr_uuids",
                          jcr_uuids.contains(n2.getProperty("jcr:uuid").getString()));
        printMessage("Confirmed: the [jcr:uuid] for both Artifact B and Artifact C were found!");

        // OK - so far so good. Now put it all together in a single query! Here
        // we are trying to select Artifact B and Artifact C by selecting all Artifacts
        // that Artifatc A has a 'relatesTo' relationship on
        query = "SELECT artifact2.* FROM [sramp:artifact] AS artifact1"
                + "   JOIN [sramp:relationship] AS relationship1 ON ISCHILDNODE(relationship1, artifact1)"
                + "   JOIN [sramp:artifact] AS artifact2 ON artifact2.[jcr:uuid] = relationship1.[sramp:target]"
                + "   WHERE artifact1.[sramp:name] = 'A' AND relationship1.[sramp:type] = 'relatesTo')";
        jcrQueryResult = assertJcrSql2Query(query, 2);
        jcrNodes = jcrQueryResult.getNodes();
        Assert.assertEquals("Expected two (2) nodes (Artifact B and Artifact C) to come back!", 2, jcrNodes.getSize());
    }

}
