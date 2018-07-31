/*
 * Copyright 2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
import static org.junit.Assert.assertNull;

import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.index.revision.RevisionBranch.BranchState;
import com.b2international.index.revision.RevisionFixtures.RevisionData;
import com.google.common.collect.ImmutableList;

/**
 * @since 7.0
 */
public class RevisionBranchMergeTest extends BaseRevisionIndexTest {

	private static final RevisionData NEW_DATA = new RevisionData(STORAGE_KEY1, "field1", "field2");
	private static final RevisionData NEW_DATA2 = new RevisionData(STORAGE_KEY2, "field1", "field2");
	private static final RevisionData CHANGED_DATA = new RevisionData(STORAGE_KEY1, "field1Changed", "field2");

	@Override
	protected Collection<Class<?>> getTypes() {
		return ImmutableList.<Class<?>>of(RevisionData.class);
	}
	
	@Test(expected = BadRequestException.class)
	public void mergeMain() throws Exception {
		branching().merge(MAIN, MAIN, "Message");
	}
	
	@Test
	public void behindStateAfterParentCommit() throws Exception {
		final String a = createBranch(MAIN, "a");
		commit(MAIN, Collections.emptySet());
		assertState(a, MAIN, BranchState.BEHIND);
	}
	
	@Test
	public void mergeEmptyUpToDateBranch() throws Exception {
		String child = createBranch(MAIN, "a");
		assertState(child, MAIN, BranchState.UP_TO_DATE);
		branching().merge(child, MAIN, "Rebase");
		assertState(child, MAIN, BranchState.UP_TO_DATE);
	}
	
	@Test
	public void mergeBehindBranch() throws Exception {
		String a = createBranch(MAIN, "a");
		assertState(a, MAIN, BranchState.UP_TO_DATE);
		indexRevision(MAIN, NEW_DATA);
		assertState(a, MAIN, BranchState.BEHIND);
		branching().merge(a, MAIN, "Merge A to MAIN");
		assertState(a, MAIN, BranchState.BEHIND);
	}
	
	@Test
	public void mergeBranchWithNewRevisionToParent() throws Exception {
		String child = createBranch(MAIN, "a");
		// create a revision on child branch
		indexRevision(child, NEW_DATA);
		branching().merge(child, MAIN, "Merge");
		// after fast-forward merge
		// 1. MAIN should be in UP_TO_DATE state compared to the child
		assertState(MAIN, child, BranchState.UP_TO_DATE);
		// 2. Child should be UP_TO_DATE state compared to the MAIN
		assertState(child, MAIN, BranchState.UP_TO_DATE);
		// 3. revision should be visible from MAIN branch
		assertNotNull(getRevision(MAIN, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void mergeBranchWithNewToParentWithNewNoConflict() throws Exception {
		String a = createBranch(MAIN, "a");
		indexRevision(MAIN, NEW_DATA);
		assertNotNull(getRevision(MAIN, RevisionData.class, STORAGE_KEY1));
		indexRevision(a, NEW_DATA2);
		assertNotNull(getRevision(a, RevisionData.class, STORAGE_KEY2));
		assertState(a, MAIN, BranchState.DIVERGED);
		branching().merge(a, MAIN, "Merge A to MAIN");
		// after merge both revisions are visible from MAIN
		assertNotNull(getRevision(MAIN, RevisionData.class, STORAGE_KEY1));
		assertNotNull(getRevision(MAIN, RevisionData.class, STORAGE_KEY2));
		// Child state should be BEHIND since it lacks one commit from MAIN
		assertState(a, MAIN, BranchState.BEHIND);
		// MAIN state should be FORWARD, since it has one extra revision and commit
		assertState(MAIN, a, BranchState.UP_TO_DATE);
	}
	
	@Test
	public void rebaseBranchOnParentWithNewRevision() throws Exception {
		String child = createBranch(MAIN, "a");
		// create a revision on MAIN branch
		indexRevision(MAIN, NEW_DATA);
		
		branching().merge(MAIN, child, "Rebase");
		// after rebase revision should be visible from child branch
		assertNotNull(getRevision(child, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void mergeBranchWithChangedRevisionToParent() throws Exception {
		indexRevision(MAIN, NEW_DATA);
		String child = createBranch(MAIN, "a");
		// create a revision on child branch
		indexChange(child, NEW_DATA, CHANGED_DATA);
		branching().merge(child, MAIN, "Merge");
		// after merge revision should be visible from MAIN branch
		RevisionData afterMerge = getRevision(MAIN, RevisionData.class, STORAGE_KEY1);
		assertDocEquals(CHANGED_DATA, afterMerge);
	}
	
	@Test
	public void rebaseBranchOnParentWithChangedRevision() throws Exception {
		indexRevision(MAIN, NEW_DATA);
		String child = createBranch(MAIN, "a");
		// create a revision on child branch
		indexChange(MAIN, NEW_DATA, CHANGED_DATA);
		branching().merge(MAIN, child, "Rebase");
		// after merge revision should be visible from MAIN branch
		RevisionData afterRebase = getRevision(child, RevisionData.class, STORAGE_KEY1);
		assertDocEquals(CHANGED_DATA, afterRebase);
	}
	
	@Test
	public void mergeBranchWithRemoveToParent() throws Exception {
		indexRevision(MAIN, NEW_DATA);
		
		String child = createBranch(MAIN, "a");
		indexRemove(child, NEW_DATA);
		
		branching().merge(child, MAIN, "Merge");
		
		assertNull(getRevision(MAIN, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void rebaseBranchOnParentWithRemove() throws Exception {
		indexRevision(MAIN, NEW_DATA);
		
		String child = createBranch(MAIN, "a");
		indexRemove(MAIN, NEW_DATA);
		
		branching().merge(MAIN, child, "Rebase");
		
		assertNull(getRevision(child, RevisionData.class, STORAGE_KEY1));
	}
	
	private void assertState(String branchPath, String compareWith, BranchState expectedState) {
		assertEquals(expectedState, branching().getBranchState(branchPath, compareWith));
	}
	
}
