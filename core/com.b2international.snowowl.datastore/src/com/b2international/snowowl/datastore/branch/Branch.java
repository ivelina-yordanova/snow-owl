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
package com.b2international.snowowl.datastore.branch;

import com.b2international.snowowl.datastore.branch.BranchImpl.BranchState;

/**
 * @since 4.1
 */
public interface Branch {

	public static final String SEPARATOR = "/";
	
	String path();

	String name();

	Branch parent();

	long baseTimestamp();

	long headTimestamp();

	BranchState state();

	BranchState state(Branch target);

	/*
	 * TODO: move this to internal class 
	 */
	void handleCommit(long commitTimestamp);

	Branch rebase();

	Branch rebase(Branch target);
	
	/**
	 * @param source - the branch to merge onto this branch
	 * @throws BranchMergeException - if source cannot be merged
	 */
	void merge(Branch source) throws BranchMergeException;

	Branch createChild(String string);

}