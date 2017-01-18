/*
 * Copyright 2017 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.id.request;

import java.util.Collection;

import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.datastore.request.BaseBranchRequestBuilder;
import com.google.common.collect.ImmutableList;

/**
 * @since 5.5
 */
public final class SnomedIdentifierPublishRequestBuilder extends BaseBranchRequestBuilder<SnomedIdentifierPublishRequestBuilder, Boolean> {

	private Collection<String> componentIds;

	public SnomedIdentifierPublishRequestBuilder setComponentId(String componentId) {
		return setComponentIds(ImmutableList.of(componentId));
	}

	public SnomedIdentifierPublishRequestBuilder setComponentIds(Collection<String> componentIds) {
		this.componentIds = componentIds;
		return getSelf();
	}
	
	@Override
	protected Request<BranchContext, Boolean> doBuild() {
		return new SnomedIdentifierPublishRequest(componentIds);
	}

}
