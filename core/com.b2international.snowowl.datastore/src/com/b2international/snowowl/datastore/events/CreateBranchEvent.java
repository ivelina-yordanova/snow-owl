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
package com.b2international.snowowl.datastore.events;

import com.b2international.snowowl.core.events.Event;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.eventbus.IHandler;
import com.b2international.snowowl.eventbus.IMessage;

/**
 * @since 4.1
 */
public class CreateBranchEvent implements Event {

	private String repository;
	private String parent;
	private String name;
	
	public CreateBranchEvent(String repository, String parent, String name) {
		this.repository = repository;
		this.parent = parent;
		this.name = name;
	}
	
	@Override
	public void send(IEventBus bus) {
		send(bus, null);
	}
	
	@Override
	public void send(IEventBus bus, IHandler<IMessage> replyHandler) {
		bus.send("/branches", this, replyHandler);
	}

	public String getParent() {
		return parent;
	}
	
	public String getName() {
		return name;
	}
	
	public String getRepository() {
		return repository;
	}
	
}
