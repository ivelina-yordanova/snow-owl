/*
 * Copyright 2017-2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.es.admin;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.ReflectionUtils;
import com.b2international.index.Analyzers;
import com.b2international.index.BulkDelete;
import com.b2international.index.BulkOperation;
import com.b2international.index.BulkUpdate;
import com.b2international.index.Doc;
import com.b2international.index.IP;
import com.b2international.index.IndexClientFactory;
import com.b2international.index.IndexException;
import com.b2international.index.Keyword;
import com.b2international.index.Text;
import com.b2international.index.admin.IndexAdmin;
import com.b2international.index.es.EsClient;
import com.b2international.index.es.query.EsQueryBuilder;
import com.b2international.index.mapping.DocumentMapping;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.query.Expressions;
import com.b2international.index.util.NumericClassUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;

/**
 * @since 5.10
 */
public final class EsIndexAdmin implements IndexAdmin {

	private static final int DEFAULT_MAX_NUMBER_OF_VERSION_CONFLICT_RETRIES = 5;
	private static final int BATCHS_SIZE = 10_000;
	
	private final Random random = new Random();
	private final EsClient client;
	private final String name;
	private final Mappings mappings;
	private final Map<String, Object> settings;
	private final ObjectMapper mapper;
	
	private final Logger log;
	private final String prefix;

	public EsIndexAdmin(EsClient client, String clientUri, String name, Mappings mappings, Map<String, Object> settings, ObjectMapper mapper) {
		this.client = client;
		this.name = name.toLowerCase();
		this.mappings = mappings;
		this.settings = newHashMap(settings);
		this.mapper = mapper;
		
		this.log = LoggerFactory.getLogger(String.format("index.%s", this.name));
		
		this.settings.putIfAbsent(IndexClientFactory.COMMIT_CONCURRENCY_LEVEL, IndexClientFactory.DEFAULT_COMMIT_CONCURRENCY_LEVEL);
		this.settings.putIfAbsent(IndexClientFactory.RESULT_WINDOW_KEY, ""+IndexClientFactory.DEFAULT_RESULT_WINDOW);
		this.settings.putIfAbsent(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY, IndexClientFactory.DEFAULT_TRANSLOG_SYNC_INTERVAL);
		
		final String prefix = (String) settings.getOrDefault(IndexClientFactory.INDEX_PREFIX, IndexClientFactory.DEFAULT_INDEX_PREFIX);
		this.prefix = prefix.isEmpty() ? "" : prefix + ".";
	}
	
	@Override
	public Logger log() {
		return log;
	}

	@Override
	public boolean exists() {
		final String[] indices = getAllIndexes();
		final GetIndexRequest getIndexRequest = new GetIndexRequest().indices(indices);

		try {
			return client().indices().exists(getIndexRequest, RequestOptions.DEFAULT);
		} catch (IOException e) {
			throw new IndexException("Couldn't check the existence of all ES indices.", e);
		}
	}

	private boolean exists(DocumentMapping mapping) {
		final String index = getTypeIndex(mapping);
		final GetIndexRequest getIndexRequest = new GetIndexRequest().indices(index);

		try {
			return client().indices().exists(getIndexRequest, RequestOptions.DEFAULT);
		} catch (IOException e) {
			throw new IndexException("Couldn't check the existence of ES index '" + index + "'.", e);
		}
	}

	@Override
	public void create() {
		if (!exists()) {
			log.info("Preparing '{}' indexes...", name);
			// create number of indexes based on number of types
	 		for (DocumentMapping mapping : mappings.getMappings()) {
	 			if (exists(mapping)) {
	 				continue;
	 			}
	 			
	 			final String index = getTypeIndex(mapping);
				final String type = mapping.typeAsString();
				final Map<String, Object> typeMapping = ImmutableMap.of(type,
					ImmutableMap.builder()
						.put("date_detection", "false")
						.put("numeric_detection", "false")
						.putAll(toProperties(mapping))
						.build());
				
				final Map<String, Object> indexSettings;
				try {
					indexSettings = createIndexSettings();
					log.info("Configuring '{}' index with settings: {}", index, indexSettings);
				} catch (IOException e) {
					throw new IndexException("Couldn't prepare settings for index " + index, e);
				}
				
				final CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
				createIndexRequest.mapping(type, typeMapping);
				createIndexRequest.settings(indexSettings);
				
				try {
					final CreateIndexResponse response = client.indices()
							.create(createIndexRequest, RequestOptions.DEFAULT);
					checkState(response.isAcknowledged(), "Failed to create index '%s' for type '%s'", name, mapping.typeAsString());
				} catch (IOException e) {
					throw new IndexException(String.format("Failed to create index '%s' for type '%s'", name, mapping.typeAsString()), e);
				}
				
	 		}
		}
		// wait until the cluster processes each index create request
		waitForYellowHealth(getAllIndexes());
		log.info("'{}' indexes are ready.", name);
	}

	private String[] getAllIndexes() {
		return mappings.getMappings()
				.stream()
				.map(this::getTypeIndex)
				.distinct()
				.toArray(String[]::new);
	}

	private Map<String, Object> createIndexSettings() throws IOException {
		InputStream analysisStream = getClass().getResourceAsStream("analysis.json");
		Settings analysisSettings = Settings.builder()
				.loadFromStream("analysis.json", analysisStream, true)
				.build();
		
		// FIXME: Is XContent a good alternative to a Map? getAsStructureMap is now private
		Map<String, Object> analysisMap = ReflectionUtils.callMethod(Settings.class, analysisSettings, "getAsStructuredMap");
		
		return ImmutableMap.<String, Object>builder()
				.put("analysis", analysisMap)
				.put("number_of_shards", String.valueOf(settings().getOrDefault(IndexClientFactory.NUMBER_OF_SHARDS, "1")))
				.put("number_of_replicas", "0")
				// disable es refresh, we will do it manually on each commit
				.put("refresh_interval", "-1")
				.put(IndexClientFactory.RESULT_WINDOW_KEY, settings().get(IndexClientFactory.RESULT_WINDOW_KEY))
				.put(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY, settings().get(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY))
				.put("translog.durability", "async")
				.build();
	}
	
	private void waitForYellowHealth(String... indices) {
		if (!CompareUtils.isEmpty(indices)) {
			/*
			 * See https://www.elastic.co/guide/en/elasticsearch/reference/6.3/cluster-health.html 
			 * for the low-level structure of the cluster health request.
			 */
			final Object clusterTimeoutSetting = settings.getOrDefault(IndexClientFactory.CLUSTER_HEALTH_TIMEOUT, IndexClientFactory.DEFAULT_CLUSTER_HEALTH_TIMEOUT);
			final Object socketTimeoutSetting = settings.getOrDefault(IndexClientFactory.SOCKET_TIMEOUT, IndexClientFactory.DEFAULT_SOCKET_TIMEOUT);
			final int clusterTimeout = clusterTimeoutSetting instanceof Integer ? (int) clusterTimeoutSetting : Integer.parseInt((String) clusterTimeoutSetting);
			final int socketTimeout = socketTimeoutSetting instanceof Integer ? (int) socketTimeoutSetting : Integer.parseInt((String) socketTimeoutSetting);
			final int pollTimeout = socketTimeout / 2;
			
			// GET /_cluster/health/test1,test2
			final String endpoint = new EsClient.EndpointBuilder()
					.addPathPartAsIs("_cluster")
					.addPathPartAsIs("health")
					.addCommaSeparatedPathParts(indices)
					.build();
			
			// https://www.elastic.co/guide/en/elasticsearch/reference/6.3/cluster-health.html#request-params
			final Map<String, String> parameters = ImmutableMap.<String, String>builder()
					.put("level", "indices") // Detail level should be concerned with the indices in the path
					.put("wait_for_status", "yellow") // Wait until yellow status is reached
					.put("timeout", String.format("%sms", pollTimeout)) // Poll interval is half the socket timeout
					.put("ignore", "408") // This parameter is not sent to ES; it makes server 408 responses not throw an exception
					.build(); 
		
			final long startTime = System.currentTimeMillis();
			final long endTime = startTime + clusterTimeout; // Polling finishes when the cluster timeout is reached
			long currentTime = startTime; 
			JsonNode responseNode = null;
			
			do {
				
				try {
					
					final Response clusterHealthResponse = client().getLowLevelClient()
							.performRequest(HttpGet.METHOD_NAME, endpoint, parameters);
					final InputStream responseStream = clusterHealthResponse.getEntity()
							.getContent();
					responseNode = mapper.readTree(responseStream);
					
					if (!responseNode.get("timed_out").asBoolean()) {
						currentTime = System.currentTimeMillis();
						break; 
					}
					
				} catch (IOException e) {
					throw new IndexException("Couldn't retrieve cluster health for index " + name, e);
				}
				
				currentTime = System.currentTimeMillis();
			
			} while (currentTime < endTime);
			
			if (responseNode == null || responseNode.get("timed_out").asBoolean()) {
				throw new IndexException(String.format("Cluster health did not reach yellow status for '%s' indexes after %s ms.", name, currentTime - startTime), null);
			} else {
				log.info("Cluster health for '{}' indexes reported as '{}' after {} ms.", name, responseNode.get("status").asText(), currentTime - startTime);
			}
		}
	}

	private Map<String, Object> toProperties(DocumentMapping mapping) {
		Map<String, Object> properties = newHashMap();
		for (Field field : mapping.getFields()) {
			final String property = field.getName();
			if (DocumentMapping._ID.equals(property)) continue;
			final Class<?> fieldType = NumericClassUtils.unwrapCollectionType(field);
			
			if (Map.class.isAssignableFrom(fieldType)) {
				// allow dynamic mappings for dynamic objects like field using Map
				final Map<String, Object> prop = newHashMap();
				prop.put("type", "object");
				prop.put("dynamic", "true");
				properties.put(property, prop);
				continue;
			} else if (fieldType.isAnnotationPresent(Doc.class)) {
				Doc annotation = fieldType.getAnnotation(Doc.class);
				// this is a nested document type create a nested mapping
				final Map<String, Object> prop = newHashMap();
				prop.put("type", annotation.nested() ? "nested" : "object");
				prop.put("enabled", annotation.index() ? true : false);
				prop.putAll(toProperties(new DocumentMapping(fieldType)));
				properties.put(property, prop);
			} else {
				final Map<String, Object> prop = newHashMap();
				
				if (!mapping.isText(property) && !mapping.isKeyword(property)) {
					addFieldProperties(prop, fieldType);
					properties.put(property, prop);
				} else {
					checkState(String.class.isAssignableFrom(fieldType), "Only String fields can have Text and Keyword annotation. Found them on '%s'", property);
					
					final Map<String, Text> textFields = mapping.getTextFields(property);
					final Map<String, Keyword> keywordFields = mapping.getKeywordFields(property);
					
					final Text textMapping = textFields.get(property);
					final Keyword keywordMapping = keywordFields.get(property);
					checkState(textMapping == null || keywordMapping == null, "Cannot declare both Text and Keyword annotation on same field '%s'", property);
					
					if (textMapping != null) {
						prop.put("type", "text");
						prop.put("analyzer", EsTextAnalysis.getAnalyzer(textMapping.analyzer()));
						if (textMapping.searchAnalyzer() != Analyzers.INDEX) {
							prop.put("search_analyzer", EsTextAnalysis.getAnalyzer(textMapping.searchAnalyzer()));
						}
					}
					
					if (keywordMapping != null) {
						prop.put("type", "keyword");
						String normalizer = EsTextAnalysis.getNormalizer(keywordMapping.normalizer());
						if (!Strings.isNullOrEmpty(normalizer)) {
							prop.put("normalizer", normalizer);
						}
						prop.put("index", keywordMapping.index());
						prop.put("doc_values", keywordMapping.index());
					}
					
					// put extra text fields into fields object
					final Map<String, Object> fields = newHashMapWithExpectedSize(textFields.size() + keywordFields.size());
					for (Entry<String, Text> analyzer : textFields.entrySet()) {
						final String extraField = analyzer.getKey();
						final String[] extraFieldParts = extraField.split(Pattern.quote(DocumentMapping.DELIMITER));
						if (extraFieldParts.length > 1) {
							final Text analyzed = analyzer.getValue();
							final Map<String, Object> fieldProps = newHashMap();
							fieldProps.put("type", "text");
							fieldProps.put("analyzer", EsTextAnalysis.getAnalyzer(analyzed.analyzer()));
							if (analyzed.searchAnalyzer() != Analyzers.INDEX) {
								fieldProps.put("search_analyzer", EsTextAnalysis.getAnalyzer(analyzed.searchAnalyzer()));
							}
							fields.put(extraFieldParts[1], fieldProps);
						}
					}
					
					// put extra keyword fields into fields object
					for (Entry<String, Keyword> analyzer : keywordFields.entrySet()) {
						final String extraField = analyzer.getKey();
						final String[] extraFieldParts = extraField.split(Pattern.quote(DocumentMapping.DELIMITER));
						if (extraFieldParts.length > 1) {
							final Keyword analyzed = analyzer.getValue();
							final Map<String, Object> fieldProps = newHashMap();
							fieldProps.put("type", "keyword");
							String normalizer = EsTextAnalysis.getNormalizer(analyzed.normalizer());
							if (!Strings.isNullOrEmpty(normalizer)) {
								fieldProps.put("normalizer", normalizer);
							}
							fieldProps.put("index", analyzed.index());
							fields.put(extraFieldParts[1], fieldProps);
						}
					}
					
					if (!fields.isEmpty()) {
						prop.put("fields", fields);
					}
					properties.put(property, prop);
				}
				
			}
		}
		
		// Add system field "_hash", if there is at least a single field to hash
		if (!mapping.getHashedFields().isEmpty()) {
			final Map<String, Object> prop = newHashMap();
			prop.put("type", "keyword");
			prop.put("index", false);
			properties.put(DocumentMapping._HASH, prop);
		}
		
		return ImmutableMap.of("properties", properties);
	}

	private void addFieldProperties(Map<String, Object> fieldProperties, Class<?> fieldType) {
		if (Enum.class.isAssignableFrom(fieldType) || NumericClassUtils.isBigDecimal(fieldType) || String.class.isAssignableFrom(fieldType)) {
			fieldProperties.put("type", "keyword");
		} else if (NumericClassUtils.isFloat(fieldType)) {
			fieldProperties.put("type", "float");
		} else if (NumericClassUtils.isInt(fieldType)) {
			fieldProperties.put("type", "integer");
		} else if (NumericClassUtils.isShort(fieldType)) {
			fieldProperties.put("type", "short");
		} else if (NumericClassUtils.isDate(fieldType) || NumericClassUtils.isLong(fieldType)) {
			fieldProperties.put("type", "long");
		} else if (Boolean.class.isAssignableFrom(Primitives.wrap(fieldType))) {
			fieldProperties.put("type", "boolean");
		} else if (fieldType.isAnnotationPresent(IP.class)) {
			fieldProperties.put("type", "ip");
		} else {
			// Any other type will result in a sub-object that only appears in _source
			fieldProperties.put("type", "object");
			fieldProperties.put("enabled", false);
		}
	}

	@Override
	public void delete() {
		if (exists()) {
			final DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(name + "*");
			try {
				final DeleteIndexResponse deleteIndexResponse = client()
						.indices()
						.delete(deleteIndexRequest, RequestOptions.DEFAULT);
				checkState(deleteIndexResponse.isAcknowledged(), "Failed to delete all ES indices for '%s'.", name);
			} catch (IOException e) {
				throw new IndexException(String.format("Failed to delete all ES indices for '%s'.", name), e);
			}
		}
	}

	@Override
	public void clear(Collection<Class<?>> types) {
		if (CompareUtils.isEmpty(types)) {
			return;
		}
		
		final Set<DocumentMapping> typesToRefresh = Collections.synchronizedSet(newHashSetWithExpectedSize(types.size()));
		
		for (Class<?> type : types) {
			bulkDelete(new BulkDelete<>(type, Expressions.matchAll()), typesToRefresh);
		}
		
		refresh(typesToRefresh);
	}

	@Override
	public Map<String, Object> settings() {
		return settings;
	}

	@Override
	public Mappings mappings() {
		return mappings;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void close() {}

	@Override
	public void optimize(int maxSegments) {
//		client().admin().indices().prepareForceMerge(name).setMaxNumSegments(maxSegments).get();
//		waitForYellowHealth();
	}
	
	public String getTypeIndex(DocumentMapping mapping) {
		if (mapping.getParent() != null) {
			return String.format("%s%s-%s", prefix, name, mapping.getParent().typeAsString());
		} else {
			return String.format("%s%s-%s", prefix, name, mapping.typeAsString());
		}
	}
	
	public EsClient client() {
		return client;
	}
	
	public void refresh(Set<DocumentMapping> typesToRefresh) {
		if (!CompareUtils.isEmpty(typesToRefresh)) {
			final String[] indicesToRefresh;
			
			synchronized (typesToRefresh) {
				indicesToRefresh = typesToRefresh.stream()
						.map(this::getTypeIndex)
						.distinct()
						.toArray(String[]::new);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Refreshing indexes '{}'", Arrays.toString(indicesToRefresh));
			}
			
			try {
				
				final RefreshRequest refreshRequest = new RefreshRequest(indicesToRefresh);
				final RefreshResponse refreshResponse = client()
						.indices()
						.refresh(refreshRequest, RequestOptions.DEFAULT);
				if (RestStatus.OK != refreshResponse.getStatus() && log.isErrorEnabled()) {
					log.error("Index refresh request of '{}' returned with status {}", Joiner.on(", ").join(indicesToRefresh), refreshResponse.getStatus());
				}
				
			} catch (IOException e) {
				throw new IndexException(String.format("Failed to refresh ES indexes '%s'.", Arrays.toString(indicesToRefresh)), e);
			}
		}
	}
	
	public void bulkUpdate(final BulkUpdate<?> update, Set<DocumentMapping> mappingsToRefresh) {
		final DocumentMapping mapping = mappings().getMapping(update.getType());
		final String rawScript = mapping.getScript(update.getScript()).script();
		org.elasticsearch.script.Script script = new org.elasticsearch.script.Script(ScriptType.INLINE, "painless", rawScript, ImmutableMap.copyOf(update.getParams()));
		bulkIndexByScroll(client, update, "_update_by_query", script, mappingsToRefresh);
	}

	public void bulkDelete(final BulkDelete<?> delete, Set<DocumentMapping> mappingsToRefresh) {
		bulkIndexByScroll(client, delete, "_delete_by_query", null, mappingsToRefresh);
	}

	private void bulkIndexByScroll(final EsClient client,
			final BulkOperation<?> op, 
			final String command, 
			final org.elasticsearch.script.Script script, 
			final Set<DocumentMapping> mappingsToRefresh) {
		
		final DocumentMapping mapping = mappings().getMapping(op.getType());
		final QueryBuilder query = new EsQueryBuilder(mapping).build(op.getFilter());
		
		long versionConflicts = 0;
		int attempts = DEFAULT_MAX_NUMBER_OF_VERSION_CONFLICT_RETRIES;
		
		do {

			/*
			 * See https://www.elastic.co/guide/en/elasticsearch/reference/6.3/docs-update-by-query.html and 
			 * for https://www.elastic.co/guide/en/elasticsearch/reference/6.3/docs-delete-by-query.html
			 * the low-level structure of this request.
			 */
			try {

				final String endpoint = String.format("%s/%s/%s", getTypeIndex(mapping), mapping.typeAsString(), command);
				
				// https://www.elastic.co/guide/en/elasticsearch/reference/6.3/docs-update-by-query.html#_url_parameters_2
				final Map<String, String> parameters = ImmutableMap.<String, String>builder()
						.put("scroll_size", Integer.toString(BATCHS_SIZE))
						.put("slices", Integer.toString(getConcurrencyLevel()))
						.build(); 

				final ObjectNode ubqr = mapper.createObjectNode();
				putXContentValue(ubqr, "script", script);
				putXContentValue(ubqr, "query", query);

				final HttpEntity requestBody = new StringEntity(mapper.writeValueAsString(ubqr), ContentType.APPLICATION_JSON);
				
				Response response;
				try {
					response = client.getLowLevelClient().performRequest(HttpPost.METHOD_NAME, endpoint, parameters, requestBody);
				} catch (ResponseException e) {
					response = e.getResponse();
				}
				
				// https://www.elastic.co/guide/en/elasticsearch/reference/6.4/docs-update-by-query.html#docs-update-by-query-response-body
				final JsonNode updateByQueryResponse = mapper.readTree(response.getEntity().getContent());
				
				final int updateCount = updateByQueryResponse.has("updated") ? updateByQueryResponse.get("updated").asInt() : 0;
				final int deleteCount = updateByQueryResponse.has("deleted") ? updateByQueryResponse.get("deleted").asInt() : 0;
				final int noops = updateByQueryResponse.has("noops") ? updateByQueryResponse.get("noops").asInt() : 0;
				final ArrayNode failures = (ArrayNode) updateByQueryResponse.get("failures");
				
				versionConflicts = updateByQueryResponse.has("version_conflicts") ? updateByQueryResponse.get("version_conflicts").asInt() : 0;
				
				boolean updated = updateCount > 0;
				if (updated) {
					mappingsToRefresh.add(mapping);
					log().info("Updated {} {} documents with bulk {}", updateCount, mapping.typeAsString(), op);
				}
				
				boolean deleted = deleteCount > 0;
				if (deleted) {
					mappingsToRefresh.add(mapping);
					log().info("Deleted {} {} documents with bulk {}", deleteCount, mapping.typeAsString(), op);
				}
				
				if (!updated && !deleted) {
					log().warn("Bulk {} could not be applied to {} documents, no-ops ({}), conflicts ({})",
							op,
							mapping.typeAsString(), 
							noops, 
							versionConflicts);
				}
				
				if (failures.size() > 0) {
					boolean versionConflictsOnly = true;
					for (JsonNode failure : failures) {
						final String failureMessage = failure.get("cause").get("reason").asText();
						final int failureStatus = failure.get("status").asInt();
						
						if (failureStatus != RestStatus.CONFLICT.getStatus()) {
							versionConflictsOnly = false;
							log().error("Index failure during bulk update: {}", failureMessage);
						} else {
							log().warn("Version conflict reason: {}", failureMessage);
						}
					}

					if (!versionConflictsOnly) {
						throw new IllegalStateException("There were indexing failures during bulk updates. See logs for all failures.");
					}
				}
				
				if (attempts <= 0) {
					throw new IndexException("There were indexing failures during bulk updates. See logs for all failures.", null);
				}
				
				if (versionConflicts > 0) {
					--attempts;
					try {
						Thread.sleep(100 + random.nextInt(900));
						refresh(Collections.singleton(mapping));
					} catch (InterruptedException e) {
						throw new IndexException("Interrupted", e);
					}
				}
			} catch (IOException e) {
				throw new IndexException("Could not execute bulk update.", e);
			}
		} while (versionConflicts > 0);
	}

	private void putXContentValue(final ObjectNode ubqr, final String key, final ToXContent toXContent) throws IOException {
		if (toXContent == null) {
			return;
		}
		
		final XContentBuilder xContentBuilder = toXContent.toXContent(JsonXContent.contentBuilder(), ToXContent.EMPTY_PARAMS);
		xContentBuilder.flush();
		xContentBuilder.close();
		
		final ByteArrayOutputStream outputStream = (ByteArrayOutputStream) xContentBuilder.getOutputStream();
		ubqr.putRawValue(key, new RawValue(outputStream.toString("UTF-8")));
	}

	public int getConcurrencyLevel() {
		return (int) settings().get(IndexClientFactory.COMMIT_CONCURRENCY_LEVEL);
	}
	
}