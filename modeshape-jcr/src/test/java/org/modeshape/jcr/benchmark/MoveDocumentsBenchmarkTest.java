/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.jcr.benchmark;

import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.JcrSession;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;

/**
 * Test to measure execution time of move documents with different configurations in cluster.
 * Report will be generated to <a href="../modeshape-jcr/target/benchmark-move-documents/report.html">file</a>
 * @author evgeniy.shevchenko
 * @version 1.0 4/29/14
 */
@BenchmarkMethodChart(filePrefix = "../modeshape-jcr/target/benchmark-move-documents/report")
public class MoveDocumentsBenchmarkTest extends AbstractBenchmarkTest {


    private static final String SOURCE_PATH = "/source";
    private static final String DEST_PATH = "/dest";
    private static final String EXPECTED_CONTENT = "Lorem ipsum";
    private static final int EXPECTED_SIZE = 11;

    /**
     * List of documents identifiers, which will be validated on all
     * cluster nodes.
     */
    private Set<String> taskResults =
            new HashSet<String>();

    @Before
    public void before() {
        taskResults.clear();
    }
    /**
     * Test async distribution mode with tcp transport
     * Full configuration can be found by this
     * <a href="file:////src/test/resources/cluster/moveDocumentsAsyncTcp/repo.json">link</a>
     * @throws Exception on error
     */
    @Ignore
    @Test
    @BenchmarkOptions(benchmarkRounds = 1, warmupRounds = 0)
    public void moveDocumentsAsyncTcp() throws Exception {
        executeTest();
    }



    /**
     * Test async replication mode with tcp transport
     * Full configuration can be found by this
     * <a href="file:////src/test/resources/cluster/moveDocumentsSyncTcp/repo.json">link</a>
     * @throws Exception on error
     */
    @Ignore
    @Test
    @BenchmarkOptions(benchmarkRounds = 1, warmupRounds = 0)
    public void moveDocumentsSyncTcp() throws Exception {
        executeTest();
    }

    /**
     * Generate list of {@see Callable} tasks.
     * One task will login to random repo in cluster and create document
     * with body.
     * @return List of tasks
     */
    @Override
    protected List<Callable<String>> generateTasks() throws RepositoryException {
        return BenchmarkTestHelper
                .generateMoveNodesTasks(repositories, SOURCE_PATH, DEST_PATH);
    }


    /**
     * Validate that all objects are available in all repositories
     * after replication.
     * @throws Exception on error
     */
    @Override
    protected void validate() throws Exception {
        for (JcrRepository repository: repositories) {
            JcrSession session = null;
            try {
                session = repository.login();
                BenchmarkTestHelper
                        .validateRepository(
                                session,
                                taskResults,
                                DEST_PATH,
                                EXPECTED_CONTENT,
                                EXPECTED_SIZE,
                                true);
                NodeIterator srcNodeIterator =
                        ((Node)session.getNode(SOURCE_PATH)).getNodes();
                assertFalse("Source folder is empty", srcNodeIterator.hasNext());
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        }
    }

    @Override
    protected void processTaskResult(String result) throws Exception {
        if (result!=null) {
            taskResults.add(result);
        }

    }

    /**
     * {@see Callable} task for moving nodes.
     * @author evgeniy.shevchenko
     * @version 1.0 3/24/14
     */

     static class MoveNodeTask implements Callable<String> {

        private JcrRepository repository;
        private String sourceId;
        private String destinationPath;
        public MoveNodeTask(
                final JcrRepository repository,
                final String sourceId,
                final String destinationPath) {
            this.repository = repository;
            this.sourceId = sourceId;
            this.destinationPath = destinationPath;
        }

        /**
         * Move node from one folder to another.
         *
         * @return document identifier
         * @throws Exception if unable to compute a result
         */
        @Override
        public String call() throws Exception {
            JcrSession session = null;
            try {
                session = repository.login();
                final Node item = session.getNodeByIdentifier(sourceId);
                final Node sourceFolder = item.getParent();
                final Node destFolder = session.getNode(destinationPath);
                System.out.println(
                            String.format(
                                    "Move node '%s' from '%s' to '%s'",
                                    item.getIdentifier(),
                                    destFolder.getIdentifier(),
                                    sourceFolder.getIdentifier()));
                session.move(item.getPath(), destinationPath + "/" + item.getName());
                session.save();
                return item.getIdentifier();

            } finally {
                if (session != null) {
                    session.logout();
                }
            }

        }
    }
}