# Grafana Dashboard for Vortex Micro-Batcher

This directory contains a Grafana dashboard configuration for monitoring Vortex micro-batcher metrics.

## Metrics Exposed

The Vortex library exposes the following Micrometer metrics:

### Counters
- `vortex.requests.submitted` - Total requests submitted
- `vortex.requests.succeeded` - Total requests that succeeded
- `vortex.requests.failed` - Total requests that failed
- `vortex.requests.replayed` - Total successful requests that were replayed
- `vortex.batches.dispatched` - Total batches dispatched

### Gauges
- `vortex.queue.depth` - Current depth of the request queue

### Timers
- `vortex.batch.dispatch.latency` - Time taken to dispatch a batch
- `vortex.request.wait.latency` - Time a request waits before being batched

## Dashboard Panels

The dashboard includes the following panels:

1. **Requests Submitted** - Rate of requests being submitted
2. **Requests Succeeded** - Rate of successful requests
3. **Requests Failed** - Rate of failed requests
4. **Requests Replayed** - Rate of replayed requests
5. **Batches Dispatched** - Rate of batch dispatches
6. **Queue Depth** - Current queue depth (with alerting for high values)
7. **Batch Dispatch Latency** - p50, p95, p99 percentiles
8. **Request Wait Latency** - p50, p95, p99 percentiles
9. **Success Rate** - Percentage of successful requests
10. **Total Requests (Cumulative)** - Cumulative counts of submitted, succeeded, and failed requests

## Installation

### Prometheus + Grafana Setup

1. **Export metrics to Prometheus:**
   ```java
   import io.micrometer.prometheus.PrometheusMeterRegistry;
   import io.prometheus.client.exporter.HTTPServer;
   
   PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(
       io.micrometer.prometheus.PrometheusConfig.DEFAULT);
   
   MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, prometheusRegistry);
   
   // Expose metrics endpoint
   HTTPServer server = new HTTPServer(8080);
   ```

2. **Import dashboard to Grafana:**
   - Open Grafana UI
   - Go to Dashboards → Import
   - Upload `grafana-dashboard.json`
   - Configure Prometheus data source
   - Adjust metric names if using different Micrometer registry

### Metric Name Mapping

The dashboard uses Prometheus-style metric names. If you're using a different Micrometer registry, you may need to adjust:

- Micrometer format: `vortex.requests.submitted`
- Prometheus format: `vortex_requests_submitted_total` (for counters)
- Prometheus format: `vortex_queue_depth` (for gauges)
- Prometheus format: `vortex_batch_dispatch_latency_bucket` (for timers/histograms)

## Customization

You can customize the dashboard by:
- Adjusting time ranges and refresh intervals
- Adding alerting rules (example included for queue depth)
- Modifying panel queries for your specific use case
- Adding additional panels for custom metrics

## Alerting

The dashboard includes an example alert for high queue depth (>100 items). You can extend this with additional alerts for:
- High failure rates
- High latency percentiles
- Low success rates
- Queue depth thresholds

