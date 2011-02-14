/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gis.spatial;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.impl.batchinsert.BatchInserter;
import org.neo4j.kernel.impl.nioneo.store.PropertyStore;


/**
 * Base class for the meta model tests.
 */
public abstract class Neo4jTestCase extends TestCase {
    private static final Map<String, String> TX_CONFIG = new HashMap<String, String>();
    static {
        TX_CONFIG.put( "neostore.nodestore.db.mapped_memory", "50M" );
        TX_CONFIG.put( "neostore.relationshipstore.db.mapped_memory", "150M" );
        TX_CONFIG.put( "neostore.propertystore.db.mapped_memory", "400M" );
        TX_CONFIG.put( "neostore.propertystore.db.strings.mapped_memory", "1000M" );
        TX_CONFIG.put( "neostore.propertystore.db.arrays.mapped_memory", "50M" );
        TX_CONFIG.put( "dump_configuration", "true" );
    }
    private static final Map<String, String> BATCH_CONFIG = new HashMap<String, String>();
    static {
        BATCH_CONFIG.put( "neostore.nodestore.db.mapped_memory", "50M" );
        BATCH_CONFIG.put( "neostore.relationshipstore.db.mapped_memory", "150M" );
        BATCH_CONFIG.put( "neostore.propertystore.db.mapped_memory", "400M" );
        BATCH_CONFIG.put( "neostore.propertystore.db.strings.mapped_memory", "1000M" );
        BATCH_CONFIG.put( "neostore.propertystore.db.arrays.mapped_memory", "500M" );
        BATCH_CONFIG.put( "dump_configuration", "true" );
    }
    private static File basePath = new File("target/var");
    private static File dbPath = new File(basePath, "neo4j-db");
    private GraphDatabaseService graphDb;
    private Transaction tx;
    private BatchInserter batchInserter;

    @Before
    protected void setUp() throws Exception {
        setUp(true);
    }

    /**
     * Configurable options for text cases, with or without deleting the previous database, and with
     * or without using the BatchInserter for higher creation speeds. Note that tests that need to
     * delete nodes or use transactions should not use the BatchInserter.
     * 
     * @param deleteDb
     * @param useBatchInserter
     * @throws Exception
     */
    protected void setUp(boolean deleteDb) throws Exception {
        super.setUp();
        reActivateDatabase(deleteDb);
    }

    /**
     * Some tests require switching between normal EmbeddedGraphDatabase and BatchInserter, so we
     * allow that with this method. We also allow deleting the previous database, if that is desired
     * (probably only the first time this is called).
     * 
     * @param deleteDb
     * @param useBatchInserter
     * @throws Exception
     */
    protected void reActivateDatabase(boolean deleteDb) throws Exception {
        if (graphDb == null) {
            graphDb = new EmbeddedGraphDatabase(dbPath.getAbsolutePath(), TX_CONFIG );
        }
        if (deleteDb) {
            deleteDatabase();
        }
    }

    @Override
    @After
    protected void tearDown() throws Exception {
        if (tx != null) {
            tx.success();
            tx.finish();
        }
        beforeShutdown();
        graphDb.shutdown();
        super.tearDown();
    }

    protected void beforeShutdown() {
    }

    protected File getBasePath() {
        return basePath;
    }

    protected File getNeoPath() {
        return dbPath;
    }

    protected static void deleteDatabase() {
        deleteFileOrDirectory(dbPath);
    }

    protected static void deleteFileOrDirectory(File file) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteFileOrDirectory(child);
            }
        } else {
            file.delete();
        }
    }
    
    protected long calculateDiskUsage(File file) {
    	if(file.isDirectory()) {
    		long count = 0;
    		for(File sub:file.listFiles()) {
    			count += calculateDiskUsage(sub);
    		}
    		return count;
    	} else {
    		return file.length();
    	}
    }

    protected long databaseDiskUsage() {
    	return calculateDiskUsage(dbPath);
    }

    protected long countNodes(Class<?> cls) {
    	return ((EmbeddedGraphDatabase)graphDb).getConfig().getGraphDbModule().getNodeManager().getNumberOfIdsInUse(cls);
    }

    protected void printDatabaseStats() {
    	System.out.println("Database stats:");
    	System.out.println("\tTotal disk usage: "+((float)databaseDiskUsage())/(1024.0*1024.0)+"MB");
    	System.out.println("\tTotal # nodes:    "+countNodes(Node.class));
    	System.out.println("\tTotal # rels:     "+countNodes(Relationship.class));
    	System.out.println("\tTotal # props:    "+countNodes(PropertyStore.class));
    }
    protected void restartTx() {
        restartTx(true);
    }

    protected void restartTx(boolean success) {
        if (tx != null) {
            if (success) {
                tx.success();
            } else {
                tx.failure();
            }
            tx.finish();
            tx = graphDb.beginTx();
        }
    }

    protected GraphDatabaseService graphDb() {
        return graphDb;
    }

    protected BatchInserter getBatchInserter() {
        return batchInserter;
    }

    protected boolean isUsingBatchInserter() {
        return batchInserter != null;
    }

    protected <T> void assertCollection(Collection<T> collection, T... expectedItems) {
        String collectionString = join(", ", collection.toArray());
        assertEquals(collectionString, expectedItems.length, collection.size());
        for (T item : expectedItems) {
            assertTrue(collection.contains(item));
        }
    }

    protected <T> Collection<T> asCollection(Iterable<T> iterable) {
        List<T> list = new ArrayList<T>();
        for (T item : iterable) {
            list.add(item);
        }
        return list;
    }

    protected <T> String join(String delimiter, T... items) {
        StringBuffer buffer = new StringBuffer();
        for (T item : items) {
            if (buffer.length() > 0) {
                buffer.append(delimiter);
            }
            buffer.append(item.toString());
        }
        return buffer.toString();
    }

    protected <T> int countIterable(Iterable<T> iterable) {
        int counter = 0;
        Iterator<T> itr = iterable.iterator();
        while (itr.hasNext()) {
            itr.next();
            counter++;
        }
        return counter;
    }
}
