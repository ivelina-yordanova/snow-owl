/*
 * Copyright 2019 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.identity;

import java.util.Date;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.collect.Iterables;

/**
 * @since 7.2
 */
public final class JWTGenerator {

	private final Algorithm algorithm;

	public JWTGenerator(final Algorithm algorithm, final String issuer) {
		this.algorithm = algorithm;
	}
	
	public String generate(String subject, Map<String, Object> claims) {
		Builder builder = JWT.create()
				.withIssuer(subject)
				.withIssuedAt(new Date());
		
		// add claims
		claims.forEach((key, value) -> {
			if (value instanceof String) {
				builder.withClaim(key, (String) value);
			} else if (value instanceof Iterable<?>) {
				builder.withArrayClaim(key, Iterables.toArray((Iterable<String>) value, String.class));
			} else if (value instanceof String[]) {
				builder.withArrayClaim(key, (String[]) value);
			}
		});
		
		return builder.sign(algorithm);
	}
	
}
