/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.fsprovider.internal;

import static org.apache.sling.api.SlingConstants.TOPIC_RESOURCE_ADDED;
import static org.apache.sling.api.SlingConstants.TOPIC_RESOURCE_CHANGED;
import static org.apache.sling.api.SlingConstants.TOPIC_RESOURCE_REMOVED;
import static org.apache.sling.fsprovider.internal.TestUtils.assertChange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.sling.fsprovider.internal.FileMonitor.ResourceChange;
import org.apache.sling.fsprovider.internal.TestUtils.ResourceListener;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.apache.sling.testing.mock.sling.junit.SlingContextCallback;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

/**
 * Test events when changing filesystem content (FileVault XML).
 */
public class FileVaultFileMonitorTest {
    
    private static final int CHECK_INTERVAL = 120;
    private static final int WAIT_INTERVAL = 250;

    private final File tempDir;
    private final ResourceListener resourceListener = new ResourceListener();
    
    public FileVaultFileMonitorTest() throws Exception {
        tempDir = Files.createTempDirectory(getClass().getName()).toFile();
    }

    @Rule
    public SlingContext context = new SlingContextBuilder(ResourceResolverType.JCR_MOCK)
        .beforeSetUp(new SlingContextCallback() {
            @Override
            public void execute(SlingContext context) throws Exception {
                // copy test content to temp. directory
                tempDir.mkdirs();
                File sourceDir = new File("src/test/resources/vaultfs-test");
                FileUtils.copyDirectory(sourceDir, tempDir);
                
                // mount temp. directory
                context.registerInjectActivateService(new FsResourceProvider(),
                        "provider.file", tempDir.getPath() + "/jcr_root",
                        "provider.filevault.filterxml.path", tempDir.getPath() + "/META-INF/vault/filter.xml",
                        "provider.roots", "/content/dam/talk.png",
                        "provider.checkinterval", CHECK_INTERVAL,
                        "provider.fs.mode", FsMode.FILEVAULT_XML.name());
                context.registerInjectActivateService(new FsResourceProvider(),
                        "provider.file", tempDir.getPath() + "/jcr_root",
                        "provider.filevault.filterxml.path", tempDir.getPath() + "/META-INF/vault/filter.xml",
                        "provider.roots", "/content/samples",
                        "provider.checkinterval", CHECK_INTERVAL,
                        "provider.fs.mode", FsMode.FILEVAULT_XML.name());
                
                // register resource change listener
                context.registerService(EventHandler.class, resourceListener,
                        EventConstants.EVENT_TOPIC, new String[] {
                                TOPIC_RESOURCE_ADDED, 
                                TOPIC_RESOURCE_CHANGED,
                                TOPIC_RESOURCE_REMOVED
                        });
            }
        })
        .afterTearDown(new SlingContextCallback() {
            @Override
            public void execute(SlingContext context) throws Exception {
                // remove temp directory
                tempDir.delete();
            }
        })
        .build();

    @Test
    public void testUpdateFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/dam/talk.png/_jcr_content/renditions/web.1280.1280.png");
        FileUtils.touch(file);
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(1, changes.size());
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions/web.1280.1280.png", TOPIC_RESOURCE_CHANGED);
    }
    
    @Test
    public void testAddFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/dam/talk.png/_jcr_content/renditions/text.txt");
        FileUtils.write(file, "newcontent");
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions/text.txt", TOPIC_RESOURCE_ADDED);
    }
    
    @Test
    public void testRemoveFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/dam/talk.png/_jcr_content/renditions/web.1280.1280.png");
        file.delete();
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions/web.1280.1280.png", TOPIC_RESOURCE_REMOVED);
    }
    
    @Test
    public void testAddFolder() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File folder = new File(tempDir, "jcr_root/content/dam/talk.png/_jcr_content/newfolder");
        folder.mkdir();
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/dam/talk.png/jcr:content", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/dam/talk.png/jcr:content/newfolder", TOPIC_RESOURCE_ADDED);
    }
    
    @Test
    public void testRemoveFolder() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File folder = new File(tempDir, "jcr_root/content/dam/talk.png/_jcr_content/renditions");
        FileUtils.deleteDirectory(folder);
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/dam/talk.png/jcr:content", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/dam/talk.png/jcr:content/renditions", TOPIC_RESOURCE_REMOVED);
    }

    @Test
    public void testUpdateContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/samples/en/.content.xml");
        FileUtils.touch(file);
        
        Thread.sleep(WAIT_INTERVAL);

        assertChange(changes, "/content/samples/en", TOPIC_RESOURCE_REMOVED);
        assertChange(changes, "/content/samples/en", TOPIC_RESOURCE_ADDED);
        assertChange(changes, "/content/samples/en/jcr:content", TOPIC_RESOURCE_ADDED);
    }
    
    @Test
    public void testAddContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/samples/fr/.content.xml");
        file.getParentFile().mkdir();
        FileUtils.write(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:app=\"http://sample.com/jcr/app/1.0\" "
                + "xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\" jcr:primaryType=\"app:Page\">\n"
                + "<jcr:content jcr:primaryType=\"app:PageContent\"/>\n"
                + "</jcr:root>");
        
        Thread.sleep(WAIT_INTERVAL);

        assertChange(changes, "/content/samples", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/samples/fr", TOPIC_RESOURCE_ADDED);
        assertChange(changes, "/content/samples/fr/jcr:content", TOPIC_RESOURCE_ADDED);
    }
    
    @Test
    public void testRemoveContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/samples/en");
        FileUtils.deleteDirectory(file);
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/samples", TOPIC_RESOURCE_CHANGED);
        assertChange(changes, "/content/samples/en", TOPIC_RESOURCE_REMOVED);
    }
    
    @Test
    public void testRemoveContentDotXmlOnly() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file = new File(tempDir, "jcr_root/content/samples/en/.content.xml");
        file.delete();
        
        Thread.sleep(WAIT_INTERVAL);

        assertEquals(2, changes.size());
        assertChange(changes, "/content/samples/en", TOPIC_RESOURCE_CHANGED);
        // this second event is not fully correct, but this is a quite special case, accept it for now 
        assertChange(changes, "/content/samples/en", TOPIC_RESOURCE_REMOVED);
    }
    
}