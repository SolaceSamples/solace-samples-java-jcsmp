/*
 * Copyright 2022-2023 Solace Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.samples.jcsmp.features.distributedtracing;


import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.concurrent.TimeUnit;

public class TracingUtil {

    private TracingUtil() {}

    public static void initManualTracing(String serviceName) {
        //OpenTelemetry Resource object
        Resource resource = Resource.getDefault().merge(Resource.create(
            Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)));

        //OpenTelemetry provides gRPC, HTTP and NoOp span exporter.
        //Change the collector host/ip and port below if it's not running on default localhost:4317
        OtlpGrpcSpanExporter spanExporterGrpc = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build();

        OtlpHttpSpanExporter spanExporterHttp = OtlpHttpSpanExporter.builder()
            .setEndpoint("http://localhost:4318")
            .addHeader("authorization", "dataKey abc123")
            .build();

        //Use OpenTelemetry SdkTracerProvider as TracerProvider
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporterHttp)
                .setScheduleDelay(100, TimeUnit.MILLISECONDS).build())
            .setResource(resource)
            .build();

        //This Instance can be used to get tracer if it is not configured as global
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(), //W3C Context Propagator
                        W3CBaggagePropagator.getInstance() //W3C Baggage Propagator
                    )
                )
            ).buildAndRegisterGlobal();
    }
}