/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.unit.core.postoffice.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import junit.framework.Assert;

import org.hornetq.api.core.SimpleString;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.persistence.GroupingInfo;
import org.hornetq.core.persistence.QueueBindingInfo;
import org.hornetq.core.persistence.impl.journal.JournalStorageManager;
import org.hornetq.core.postoffice.PostOffice;
import org.hornetq.core.postoffice.impl.DuplicateIDCacheImpl;
import org.hornetq.core.server.Queue;
import org.hornetq.core.transaction.impl.ResourceManagerImpl;
import org.hornetq.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.hornetq.tests.unit.util.FakePagingManager;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.ExecutorFactory;
import org.hornetq.utils.OrderedExecutorFactory;
import org.hornetq.utils.Pair;

/**
 * A DuplicateDetectionUnitTest
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public class DuplicateDetectionUnitTest extends ServiceTestBase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   ExecutorService executor;

   ExecutorFactory factory;

   @Override
   protected void tearDown() throws Exception
   {
      super.tearDown();
      executor.shutdown();
   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      executor = Executors.newSingleThreadExecutor();
      factory = new OrderedExecutorFactory(executor);
   }

   // Public --------------------------------------------------------

   public void testReloadDuplication() throws Exception
   {

      JournalStorageManager journal = null;

      try
      {
         clearData();

         SimpleString ADDRESS = new SimpleString("address");

         Configuration configuration = createDefaultConfig();

         PostOffice postOffice = new FakePostOffice();

         ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(ConfigurationImpl.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE);

         journal = new JournalStorageManager(configuration, factory, null);

         journal.start();
         journal.loadBindingJournal(new ArrayList<QueueBindingInfo>(), new ArrayList<GroupingInfo>());

         HashMap<SimpleString, List<Pair<byte[], Long>>> mapDups = new HashMap<SimpleString, List<Pair<byte[], Long>>>();

         journal.loadMessageJournal(postOffice,
                                    new FakePagingManager(),
                                    new ResourceManagerImpl(0, 0, scheduledThreadPool),
                                    new HashMap<Long, Queue>(),
                                    null,
                                    mapDups,
                                    null);

         Assert.assertEquals(0, mapDups.size());

         DuplicateIDCacheImpl cacheID = new DuplicateIDCacheImpl(ADDRESS, 10, journal, true);

         for (int i = 0; i < 100; i++)
         {
            cacheID.addToCache(RandomUtil.randomBytes(), null);
         }

         journal.stop();

         journal = new JournalStorageManager(configuration, factory, null);
         journal.start();
         journal.loadBindingJournal(new ArrayList<QueueBindingInfo>(), new ArrayList<GroupingInfo>());

         journal.loadMessageJournal(postOffice,
                                    new FakePagingManager(),
                                    new ResourceManagerImpl(0, 0, scheduledThreadPool),
                                    new HashMap<Long, Queue>(),
                                    null,
                                    mapDups,
                                    null);

         Assert.assertEquals(1, mapDups.size());

         List<Pair<byte[], Long>> values = mapDups.get(ADDRESS);

         Assert.assertEquals(10, values.size());

         cacheID = new DuplicateIDCacheImpl(ADDRESS, 10, journal, true);
         cacheID.load(values);

         for (int i = 0; i < 100; i++)
         {
            cacheID.addToCache(RandomUtil.randomBytes(), null);
         }

         journal.stop();

         mapDups.clear();

         journal = new JournalStorageManager(configuration, factory, null);
         journal.start();
         journal.loadBindingJournal(new ArrayList<QueueBindingInfo>(), new ArrayList<GroupingInfo>());

         journal.loadMessageJournal(postOffice,
                                    new FakePagingManager(),
                                    new ResourceManagerImpl(0, 0, scheduledThreadPool),
                                    new HashMap<Long, Queue>(),
                                    null,
                                    mapDups,
                                    null);

         Assert.assertEquals(1, mapDups.size());

         values = mapDups.get(ADDRESS);

         Assert.assertEquals(10, values.size());
      }
      finally
      {
         if (journal != null)
         {
            try
            {
               journal.stop();
            }
            catch (Throwable ignored)
            {
            }
         }
      }

   }
}
