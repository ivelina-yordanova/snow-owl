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
package com.b2international.snowowl.snomed.datastore.request;

import java.io.IOException;

import com.b2international.index.Hits;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.datastore.request.RevisionSearchRequest;
import com.b2international.snowowl.snomed.core.domain.constraint.SnomedConstraints;
import com.b2international.snowowl.snomed.datastore.snor.PredicateIndexEntry;

/**
 * @since 4.7
 */
public class SnomedConstraintSearchRequest extends RevisionSearchRequest<SnomedConstraints> {

	@Override
	protected SnomedConstraints doExecute(BranchContext context) throws IOException {
		final RevisionSearcher searcher = context.service(RevisionSearcher.class);
		final ExpressionBuilder queryBuilder = Expressions.builder();
		
		addComponentIdFilter(queryBuilder);
		
		final Query<PredicateIndexEntry> query = Query.builder(PredicateIndexEntry.class)
				.selectAll()
				.where(queryBuilder.build())
				.offset(offset())
				.limit(limit())
				.build();
		
		final Hits<PredicateIndexEntry> hits = searcher.search(query);
		return new SnomedConstraints(hits.getHits(), offset(), limit(), hits.getTotal());
	}

	@Override
	protected Class<SnomedConstraints> getReturnType() {
		return SnomedConstraints.class;
	}

}
