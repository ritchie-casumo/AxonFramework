/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore;

import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventstore.EventStoreTestUtils.*;
import static org.axonframework.eventstore.EventUtils.asStream;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rene de Waele
 */
@Transactional
public abstract class AbstractEventStorageEngineTest extends EventStorageEngineTest {

    private AbstractEventStorageEngine testSubject;
    private final Serializer serializer = new XStreamSerializer();

    @Test(expected = ConcurrencyException.class)
    public void testUniqueKeyConstraintOnEventIdentifier() {
        testSubject.appendEvents(createEvent("id", AGGREGATE, 0), createEvent("id", "otherAggregate", 0));
    }

    @Test
    @DirtiesContext
    public void testStoreAndLoadEventsWithUpcaster() {
        UpcasterChain mockUpcasterChain = mock(UpcasterChain.class);
        when(mockUpcasterChain.upcast(isA(SerializedObject.class), isA(UpcastingContext.class)))
                .thenAnswer(invocation -> {
                    SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                    return asList(serializedObject, serializedObject);
                });
        testSubject.setUpcasterChain(mockUpcasterChain);

        testSubject.appendEvents(createEvents(4));
        List<DomainEventMessage> upcastedEvents = asStream(testSubject.readEvents(AGGREGATE)).collect(toList());
        assertEquals(8, upcastedEvents.size());

        Iterator<DomainEventMessage> iterator = upcastedEvents.iterator();
        while (iterator.hasNext()) {
            DomainEventMessage event1 = iterator.next(), event2 = iterator.next();
            assertEquals(event1.getAggregateIdentifier(), event2.getAggregateIdentifier());
            assertEquals(event1.getSequenceNumber(), event2.getSequenceNumber());
            assertEquals(event1.getPayload(), event2.getPayload());
            assertEquals(event1.getMetaData(), event2.getMetaData());
        }
    }

    @Test(expected = ConcurrencyException.class)
    public void testStoreDuplicateEventWithExceptionTranslator() {
        testSubject.appendEvents(createEvent(0), createEvent(0));
    }

    @DirtiesContext
    @Test(expected = EventStoreException.class)
    public void testStoreDuplicateEventWithoutExceptionResolver() {
        testSubject.setPersistenceExceptionResolver(null);
        testSubject.appendEvents(createEvent(0), createEvent(0));
    }

    protected void setTestSubject(AbstractEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
        testSubject.setSerializer(serializer);
    }

}
