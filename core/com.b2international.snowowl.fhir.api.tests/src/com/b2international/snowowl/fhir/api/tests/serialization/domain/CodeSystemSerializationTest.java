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
package com.b2international.snowowl.fhir.api.tests.serialization.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.b2international.snowowl.fhir.api.tests.FhirTest;
import com.b2international.snowowl.fhir.core.codesystems.BundleType;
import com.b2international.snowowl.fhir.core.codesystems.ConceptProperties;
import com.b2international.snowowl.fhir.core.codesystems.PublicationStatus;
import com.b2international.snowowl.fhir.core.model.BooleanConceptProperty;
import com.b2international.snowowl.fhir.core.model.Bundle;
import com.b2international.snowowl.fhir.core.model.CodeSystem;
import com.b2international.snowowl.fhir.core.model.CodingConceptProperty;
import com.b2international.snowowl.fhir.core.model.Entry;
import com.b2international.snowowl.fhir.core.model.SupportedConceptProperty;
import com.b2international.snowowl.fhir.core.model.dt.Coding;
import com.b2international.snowowl.fhir.core.model.dt.Uri;


/**
 * Test for checking the serialization from model->JSON.
 * @since 6.3
 */
public class CodeSystemSerializationTest extends FhirTest {
	
	@Rule
	public ExpectedException exception = ExpectedException.none();
	
	@Test
	public void conceptPropertyTest() throws Exception {
		SupportedConceptProperty conceptProperty = SupportedConceptProperty.builder(ConceptProperties.INACTIVE).build();
		printPrettyJson(conceptProperty);
		printJson(conceptProperty);
		
		String expectedJson = "{\"code\":\"inactive\","
				+ "\"uri\":\"http://hl7.org/fhir/concept-properties/inactive\","
				+ "\"description\":\"Inactive\",\"type\":\"boolean\"}";
		
		assertEquals(expectedJson, objectMapper.writeValueAsString(conceptProperty));
		
	}
	
	@Test
	public void returnedConceptPropertyTest() throws Exception {
		BooleanConceptProperty conceptProperty = BooleanConceptProperty.builder()
				.code(ConceptProperties.INACTIVE.getCode())
				.value(true)
				.build();
		
		printPrettyJson(conceptProperty);
		printJson(conceptProperty);
		
		String expectedJson =  "[{\"name\":\"code\","
					+ "\"valueCode\":\"inactive\"},"
					+ "{\"name\":\"valueBoolean\","
					+ "\"valueBoolean\":true}]";
		
		assertEquals(expectedJson, objectMapper.writeValueAsString(conceptProperty));
		
	}
	
	@Test
	public void returnedConceptCodingPropertyTest() throws Exception {
		CodingConceptProperty conceptProperty = CodingConceptProperty.builder()
				.code(ConceptProperties.CHILD.getCode())
				.value(new Coding.Builder()
					.code("codingCode")
					.system("uri")
					.build())
				.build();
		
		printPrettyJson(conceptProperty);
		printJson(conceptProperty);
		
		String expectedJson = "[{\"name\":\"code\",\"valueCode\":\"child\"},"
				+ "{\"name\":\"valueCoding\","
				+ "\"valueCoding\":{\"code\":\"codingCode\","
				+ "\"system\":\"uri\",\"userSelected\":false}}]";
		
		assertEquals(expectedJson, objectMapper.writeValueAsString(conceptProperty));
		
	}
	
	@Test
	public void bundleTest() throws Exception {
		
		CodeSystem codeSystem = CodeSystem.builder("repo/shortName")
			.status(PublicationStatus.ACTIVE)
			.name("Local code system")
			.url(new Uri("code system uri"))
			.build();
		
		Entry entry = new Entry(new Uri("full Url"), codeSystem);
		
		Bundle bundle = Bundle.builder("bundle_Id?")
			.language("en")
			.total(1)
			.type(BundleType.SEARCHSET)
			.addLink("self", "http://localhost:8080/snowowl/CodeSystem")
			.addEntry(entry)
			.build();
		
		printPrettyJson(bundle);
		
		String expectedJson = "{\"resourceType\":\"Bundle\","
				+ "\"id\":\"bundle_Id?\","
				+ "\"language\":\"en\","
				+ "\"type\":\"searchset\","
				+"\"total\":1,"
				+ "\"link\":[{\"relation\":\"self\","
					+ "\"url\":\"http://localhost:8080/snowowl/CodeSystem\"}],"
				+ "\"entry\":[{\"fullUrl\":\"full Url\","
					+ "\"resource\":{\"resourceType\":\"CodeSystem\","
					+ "\"id\":\"repo/shortName\","
					+ "\"language\":\"en\","
					+ "\"url\":\"code system uri\","
					+ "\"name\":\"Local code system\","
					+ "\"status\":\"active\"}}]}";
		
		assertEquals(expectedJson, objectMapper.writeValueAsString(bundle));
		
	}
	

}
