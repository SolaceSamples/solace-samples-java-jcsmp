package com.solace.samples.jcsmp.features.distributedtracing;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class CustomTextMapGetter implements TextMapGetter<Object> {

	private static final List<String> FIELDS = Collections.unmodifiableList(
			Arrays.asList(new String[] { "tracestate", "traceparent", "baggage" }));

	public Iterable<String> keys(Object carrier) {
		return FIELDS;
	}

	@SuppressWarnings("unchecked")
	public String get(Object map, String key) {
		if (null == key || key.isEmpty() || null == map) {
			return null;
		}
		String traceId = ((Map<String, String>)map).get("otel_parent_trace_id");		    		
		String spanId = ((Map<String, String>)map).get("otel_parent_span_id");

		if ("tracestate".equals(key))  return null;
		if ("traceparent".equals(key)) {
			if (traceId == null || spanId == null) {
				return null;	
			} else {
				return "00-" + traceId + "-" + spanId + "-01";
			}
		}
		if ("baggage".equals(key)) return null;
		return "";
	}
}
