/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.datastore.server.snomed.index;

import static com.b2international.snowowl.core.ApplicationContext.getServiceForClass;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.b2international.commons.BooleanUtils;
import com.b2international.commons.CompareUtils;
import com.b2international.commons.collect.LongSets;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.snomed.datastore.DataTypeUtils;
import com.b2international.snowowl.snomed.datastore.PredicateUtils;
import com.b2international.snowowl.snomed.datastore.PredicateUtils.ConstraintDomain;
import com.b2international.snowowl.snomed.datastore.SnomedTaxonomyService;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService;
import com.b2international.snowowl.snomed.datastore.snor.PredicateIndexEntry;
import com.b2international.snowowl.snomed.datastore.snor.PredicateIndexEntry.PredicateType;
import com.b2international.snowowl.snomed.mrcm.GroupRule;
import com.b2international.snowowl.snomed.mrcm.HierarchyInclusionType;
import com.b2international.snowowl.snomed.snomedrefset.DataType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * Lucene based predicate browser implementation.
 * 
 */
public class SnomedServerPredicateBrowser {

//	@Override
//	public Set<ConstraintDomain> getConstraintDomains(final IBranchPath branchPath, final long storageKey) {
//		return service.executeReadTransaction(branchPath, new IndexRead<Set<ConstraintDomain>>() {
//			@Override
//			public Set<ConstraintDomain> execute(IndexSearcher index) throws IOException {
//				final String predicateKeyPrefix = String.format("%s%s", storageKey, PredicateUtils.PREDICATE_SEPARATOR);
//				final Query query = SnomedMappings.newQuery()
//						.concept()
//						.and(new PrefixQuery(SnomedMappings.componentReferringPredicate().toTerm(predicateKeyPrefix)))
//						.matchAll();
//				
//				final DocIdCollector collector = DocIdCollector.create(index.getIndexReader().maxDoc());
//				index.search(query, collector);
//				final DocIds docs = collector.getDocIDs();
//				if (docs.size() > 0) {
//					final Set<ConstraintDomain> result = newHashSet();
//					final DocIdsIterator iterator = docs.iterator();
//					while (iterator.next()) {
//						final Document doc = index.doc(iterator.getDocID(), SnomedMappings.fieldsToLoad().id().componentReferringPredicate().build());
//						for (final Iterator<IndexableField> itr = doc.iterator(); itr.hasNext();) {
//							final IndexableField indexableField = itr.next();
//							if (indexableField.name().equals(SnomedMappings.componentReferringPredicate().fieldName()) && !indexableField.stringValue().startsWith(predicateKeyPrefix)) {
//								itr.remove();
//							}
//						}
//						result.addAll(ConstraintDomain.of(doc));
//					}
//					return result;
//				}
//				return Collections.emptySet();
//			}
//		});
//	}
	
	public Collection<PredicateIndexEntry> getPredicates(final IBranchPath branchPath, final String conceptId, final @Nullable String ruleRefSetId) {
		checkNotNull(conceptId, "Concept ID must not be null.");

		final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates = getServiceForClass(ISnomedComponentService.class).getPredicates(branchPath);
		final HashSet<PredicateIndexEntry> newPredicates = newHashSet();

		addPredicatesForFocus(conceptId, HierarchyInclusionType.SELF, predicates, newPredicates);
		addPredicatesForFocus(conceptId, HierarchyInclusionType.SELF_OR_DESCENDANT, predicates, newPredicates);
		
		final SnomedConceptDocument concept = getServiceForClass(SnomedTerminologyBrowser.class).getConcept(branchPath, conceptId);

		// XXX use both the stated and inferred parent/ancestor IDs to get all possible/applicable MRCM rules
		final Builder<String> ancestorIds = ImmutableSet.builder();
		if (concept.getParents() != null) {
			ancestorIds.addAll(LongSets.toStringSet(concept.getParents()));
		}
		if (concept.getAncestors() != null) {
			ancestorIds.addAll(LongSets.toStringSet(concept.getAncestors()));
		}
		if (concept.getStatedParents() != null) {
			ancestorIds.addAll(LongSets.toStringSet(concept.getStatedParents()));
		}
		if (concept.getStatedAncestors() != null) {
			ancestorIds.addAll(LongSets.toStringSet(concept.getStatedAncestors()));
		}
		
		addDescendantPredicatesForAncestors(ancestorIds.build(), predicates, newPredicates);
		
		final Collection<String> containerRefSetIds = newHashSet(); 
		containerRefSetIds.addAll(concept.getReferringPredicates());
		containerRefSetIds.addAll(concept.getReferringMappingRefSets());
		if (null != ruleRefSetId) {
			containerRefSetIds.add(ruleRefSetId);
		}
		
		addRefSetPredicates(containerRefSetIds, predicates, newPredicates);
		addRelationshipPredicates(branchPath, conceptId, predicates, newPredicates);
		
		return newPredicates;
	}

	public Collection<PredicateIndexEntry> getPredicates(final IBranchPath branchPath, final Iterable<String> ruleParentIds, final @Nullable String ruleRefSetId) {
		checkNotNull(ruleParentIds, "Parent IDs iterable must not be null.");
		checkArgument(!Iterables.isEmpty(ruleParentIds), "Parent IDs iterable must not be empty.");
		
		final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates = getServiceForClass(ISnomedComponentService.class).getPredicates(branchPath);
		final HashSet<PredicateIndexEntry> newPredicates = newHashSet();
		
		for (final String ruleParentId : ruleParentIds) {
			// XXX: for a direct child of ruleParentId, ruleParentId itself should be treated as an ancestor, so include it 
			final ImmutableList.Builder<String> ancestorIds = ImmutableList.builder();
			ancestorIds.add(ruleParentId);
			ancestorIds.addAll(getServiceForClass(SnomedTaxonomyService.class).getAllSupertypes(branchPath, ruleParentId));
			
			addDescendantPredicatesForAncestors(ancestorIds.build(), predicates, newPredicates);
		}
		
		if (null != ruleRefSetId) {
			final Collection<String> containerRefSetIds = ImmutableList.of(ruleRefSetId);
			addRefSetPredicates(containerRefSetIds, predicates, newPredicates);	
		}
		
		return newPredicates;
	}
	
	private void addPredicatesForFocus(final String conceptId,
			final HierarchyInclusionType inclusionType,
			final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates,
			final HashSet<PredicateIndexEntry> newPredicates) {

		newPredicates.addAll(predicates.get(inclusionType).get(conceptId));
	}

	private void addDescendantPredicatesForAncestors(final Iterable<String> ancestorIds,
			final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates,
			final HashSet<PredicateIndexEntry> newPredicates) {

		for (final String ancestorId : ancestorIds) {
			addPredicatesForFocus(ancestorId, HierarchyInclusionType.SELF_OR_DESCENDANT, predicates, newPredicates);
			addPredicatesForFocus(ancestorId, HierarchyInclusionType.DESCENDANT, predicates, newPredicates);
		}
	}

	private void addRefSetPredicates(final Collection<String> containerRefSetIds,
			final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates,
			final HashSet<PredicateIndexEntry> newPredicates) {
		
		for (final String containerRefSetId : containerRefSetIds) {
			// FIXME: We do not distinguish between a concept rule on the refset identifier concept, and a refset rule
			addPredicatesForFocus(containerRefSetId, HierarchyInclusionType.SELF, predicates, newPredicates);	
		}
	}

	private void addRelationshipPredicates(final IBranchPath branchPath, final String conceptId,
			final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates,
			final HashSet<PredicateIndexEntry> newPredicates) {
	
		for (final String typeId : getServiceForClass(SnomedTaxonomyService.class).getOutboundRelationshipTypes(branchPath, conceptId)) {
			for (final String outboundId: getServiceForClass(SnomedTaxonomyService.class).getOutboundConcepts(branchPath, conceptId, typeId)) {
				newPredicates.addAll(predicates.get(HierarchyInclusionType.SELF).get(typeId + PredicateUtils.PREDICATE_SEPARATOR + outboundId));
			}
		}
	}

}