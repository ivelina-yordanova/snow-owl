/*
 * Copyright 2011-2016 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.index.entry;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.junit.Test;

import com.b2international.collections.PrimitiveCollectionModule;
import com.b2international.collections.PrimitiveSets;
import com.b2international.index.revision.BaseRevisionIndexTest;
import com.b2international.index.revision.RevisionBranch;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 4.7
 */
public class SnomedConceptIndexEntrySerializationTest extends BaseRevisionIndexTest {

	@Override
	protected void configureMapper(ObjectMapper mapper) {
		mapper.registerModule(new PrimitiveCollectionModule());
	}
	
	@Override
	protected Collection<Class<?>> getTypes() {
		return Collections.<Class<?>>singleton(SnomedConceptDocument.class);
	}
	
	@Test
	public void indexConcept() throws Exception {
		final SnomedConceptDocument concept = SnomedConceptDocument.builder()
				.id(Concepts.ROOT_CONCEPT)
				.iconId(Concepts.ROOT_CONCEPT)
				.active(true)
				.released(true)
				.effectiveTime(new Date().getTime())
				.moduleId(Concepts.MODULE_ROOT)
				.exhaustive(true)
				.primitive(true)
				.parents(PrimitiveSets.newLongOpenHashSet(-1L))
				.ancestors(PrimitiveSets.newLongOpenHashSet(-1L))
				.statedAncestors(PrimitiveSets.newLongOpenHashSet(-1L))
				.statedParents(PrimitiveSets.newLongOpenHashSet(-1L))
				.build();
		
		indexRevision(RevisionBranch.MAIN_PATH, STORAGE_KEY1, concept);
		final SnomedConceptDocument actual = getRevision(RevisionBranch.MAIN_PATH, SnomedConceptDocument.class, STORAGE_KEY1);
		assertDocEquals(concept, actual);
	}

}
