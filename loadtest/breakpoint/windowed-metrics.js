'use strict';

function createRing(windowMs) {
  if (!Number.isFinite(windowMs) || windowMs <= 0) {
    throw new TypeError('windowMs must be a positive number');
  }

  return {
    windowMs,
    samples: [],
    errors: [],
  };
}

function prune(ring, now) {
  const cutoff = now - ring.windowMs;
  ring.samples = ring.samples.filter((sample) => sample.timestamp > cutoff);
  ring.errors = ring.errors.filter((timestamp) => timestamp > cutoff);
}

function addSample(ring, value, now = Date.now()) {
  if (!Number.isFinite(value) || value < 0) {
    return false;
  }

  prune(ring, now);
  ring.samples.push({ timestamp: now, value });
  return true;
}

function addError(ring, now = Date.now()) {
  prune(ring, now);
  ring.errors.push(now);
}

function percentile(sortedValues, percentileValue) {
  if (sortedValues.length === 0) {
    return 0;
  }

  const index = Math.min(
    sortedValues.length - 1,
    Math.max(0, Math.ceil((percentileValue / 100) * sortedValues.length) - 1),
  );
  return sortedValues[index];
}

function snapshot(ring, now = Date.now()) {
  prune(ring, now);

  const values = ring.samples
    .map((sample) => sample.value)
    .sort((left, right) => left - right);
  const errorCount = ring.errors.length;
  const totalCount = values.length + errorCount;

  return {
    count: values.length,
    errorCount,
    errorRate: totalCount === 0 ? 0 : errorCount / totalCount,
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    p99: percentile(values, 99),
  };
}

module.exports = {
  createRing,
  addSample,
  addError,
  snapshot,
};
