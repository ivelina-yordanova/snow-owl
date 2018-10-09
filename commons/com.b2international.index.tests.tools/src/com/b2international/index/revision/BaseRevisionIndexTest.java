/*
 * Copyright 2011-2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.revision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;

import com.b2international.commons.options.MetadataImpl;
import com.b2international.index.DefaultIndex;
import com.b2international.index.Hits;
import com.b2international.index.Index;
import com.b2international.index.IndexClient;
import com.b2international.index.Indexes;
import com.b2international.index.mapping.DocumentMapping;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.query.Query;
import com.b2international.index.util.Reflections;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 4.7
 */
public abstract class BaseRevisionIndexTest {
	
	protected static final String MAIN = RevisionBranch.MAIN_PATH;
	protected static final String STORAGE_KEY1 = "1";
	protected static final String STORAGE_KEY2 = "2";
	
	// XXX start from 3 to take the two constant values above into account
	private AtomicLong storageKeys = new AtomicLong(3);
	private ObjectMapper mapper;
	private Mappings mappings;
	private Index rawIndex;
	private RevisionIndex index;
	private TimestampProvider timestampProvider;
	
	protected final String nextId() {
		return Long.toString(storageKeys.getAndIncrement());
	}
	
	@Before
	public void setup() {
		mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		configureMapper(mapper);
		mappings = new Mappings(getTypes());
		rawIndex = new DefaultIndex(createIndexClient(mapper, mappings));
		timestampProvider = new TimestampProvider.Default();
		index = new DefaultRevisionIndex(rawIndex, timestampProvider, mapper);
		index.admin().create();
	}

	@After
	public void teardown() {
		if (index != null) {
			index.admin().delete();
		}
	}
	
	protected final ObjectMapper getMapper() {
		return mapper;
	}
	
	protected void configureMapper(ObjectMapper mapper) {
	}
	
	protected String createBranch(String parent, String child) {
		return branching().createBranch(parent, child, new MetadataImpl());
	}
	
	protected long currentTime() {
		return ((DefaultRevisionBranching) branching()).currentTime();
	}

	protected final RevisionIndex index() {
		return index;
	}
	
	protected final Index rawIndex() {
		return rawIndex;
	}
	
	protected BaseRevisionBranching branching() {
		return index().branching();
	}
	
	protected RevisionBranch getMainBranch() {
		return getBranch(MAIN);
	}
	
	protected RevisionBranch getBranch(String branchPath) {
		return branching().getBranch(branchPath);
	}
	
	/**
	 * Subclasses may override to provide additional mappings for the underlying index.
	 * @return
	 */
	protected Collection<Class<?>> getTypes() {
		return Collections.emptySet();
	}
	
	private final IndexClient createIndexClient(ObjectMapper mapper, Mappings mappings) {
		return Indexes.createIndexClient(UUID.randomUUID().toString(), mapper, mappings);
	}
	
	protected final void indexDocument(final String key, final Object doc) {
		rawIndex().write(index -> {
			index.put(key, doc);
			index.commit();
			return null;
		});
	}
	
	protected final <T extends Revision> T getRevision(final String branch, final Class<T> type, final String key) {
		return index().read(branch, index -> index.get(type, key));
	}
	
	protected final void indexRevision(final String branchPath, final Revision data) {
		commit(branchPath, Collections.singleton(data));
	}
	
	protected final void indexChange(final String branchPath, final Revision oldRevision, final Revision newRevision) {
		final long commitTimestamp = currentTime();
		index().prepareCommit(branchPath)
			.stageChange(oldRevision, newRevision)
			.commit(commitTimestamp, UUID.randomUUID().toString(), "Commit")
			.getTimestamp();
	}
	
	protected final void indexRemove(final String branchPath, final Revision...removedRevisions) {
		final long commitTimestamp = currentTime();
		StagingArea staging = index().prepareCommit(branchPath);
		Arrays.asList(removedRevisions).forEach(staging::stageRemove);
		staging
			.commit(commitTimestamp, UUID.randomUUID().toString(), "Commit")
			.getTimestamp();
	}

	protected final long commit(final String branchPath, final Collection<Revision> newRevisions) {
		final long commitTimestamp = currentTime();
		StagingArea staging = index().prepareCommit(branchPath);
		newRevisions.forEach(rev -> staging.stageNew(rev.getId(), rev));
		return staging
				.commit(commitTimestamp, UUID.randomUUID().toString(), "Commit")
				.getTimestamp();
	}
	
	protected final void deleteRevision(final String branchPath, final Class<? extends Revision> type, final String key) {
		final long commitTimestamp = currentTime();
		StagingArea staging = index().prepareCommit(branchPath);
		staging.stageRemove(key, getRevision(branchPath, type, key));
		staging.commit(commitTimestamp, UUID.randomUUID().toString(), "Commit");
	}
	
	protected final <T> Hits<T> search(final String branchPath, final Query<T> query) {
		return index().read(branchPath, index -> index.search(query));
	}
	
	protected final <T> Hits<T> searchRaw(final Query<T> query) {
		return rawIndex().read(index -> index.search(query));
	}
	
	protected void assertDocEquals(Object expected, Object actual) {
		assertNotNull("Actual document is missing from index", actual);
		for (Field f : mappings.getMapping(expected.getClass()).getFields()) {
			if (Revision.Fields.CREATED.equals(f.getName()) 
					|| Revision.Fields.REVISED.equals(f.getName())
					|| DocumentMapping._ID.equals(f.getName())) {
				// skip revision fields from equality check
				continue;
			}
			assertEquals(String.format("Field '%s' should be equal", f.getName()), Reflections.getValue(expected, f), Reflections.getValue(actual, f));
		}
	}
	
}