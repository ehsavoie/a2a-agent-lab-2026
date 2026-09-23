"""
Add this initialization code to the top of travel_agent.py (before main())
to enable OpenTelemetry tracing for the Python Travel & Logistics Agent.
"""

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def setup_otel():
    resource = Resource.create({"service.name": "Travel & Logistics Agent (Python)"})
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)
    return trace.get_tracer("travel-logistics-agent")


tracer = setup_otel()
