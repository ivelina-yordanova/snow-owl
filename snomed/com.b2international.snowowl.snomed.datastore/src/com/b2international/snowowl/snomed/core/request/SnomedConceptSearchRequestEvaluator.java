/*
 * Copyright 2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.core.request;

import java.util.stream.Collectors;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.options.Options;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.datastore.request.SnomedConceptSearchRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.ecl.Ecl;

/**
 * @since 7.5
 */
public final class SnomedConceptSearchRequestEvaluator implements ConceptSearchRequestEvaluator {

	@Override
	public Concepts evaluate(BranchContext context, Options search) {
		final SnomedConceptSearchRequestBuilder req = SnomedRequests.prepareSearchConcept();
		
		if (search.containsKey(OptionKey.ID)) {
			req.filterByIds(search.getCollection(OptionKey.ID, String.class));
		}
		
		if (search.containsKey(OptionKey.TERM)) {
			req.filterByTerm(search.getString(OptionKey.TERM));
		}
		
		if (search.containsKey(OptionKey.QUERY) || search.containsKey(OptionKey.MUST_NOT_QUERY)) {
			StringBuilder query = new StringBuilder();
			
			if (search.containsKey(OptionKey.QUERY)) {
				query.append(Ecl.or(search.getCollection(OptionKey.QUERY, String.class)));
			} else {
				query.append(Ecl.ANY);
			}
			
			if (search.containsKey(OptionKey.MUST_NOT_QUERY)) {
				query
					.append(" MINUS ")
					.append(Ecl.or(search.getCollection(OptionKey.MUST_NOT_QUERY, String.class)));
			}
			
			req.filterByQuery(query.toString());
		}
		
		SnomedConcepts matches = req
				.setLocales(search.getList(OptionKey.LOCALES, ExtendedLocale.class))
				.setSearchAfter(search.getString(OptionKey.AFTER))
				.setLimit(search.get(OptionKey.LIMIT, Integer.class))
				.setExpand("pt(),fsn()")
				.build()
				.execute(context);

		return new Concepts(matches.stream().map(this::toConcept).collect(Collectors.toList()), matches.getSearchAfter(), matches.getLimit(), matches.getTotal());
	}
	
	private Concept toConcept(SnomedConcept concept) {
		Concept result = new Concept();
		result.setId(concept.getId());
		result.setReleased(concept.isReleased());
		result.setIconId(concept.getIconId());
		result.setTerm(concept.getPt() == null ? concept.getId() : concept.getPt().getTerm());
		return result;
	}
	
}

