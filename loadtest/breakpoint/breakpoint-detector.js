'use strict';

const DEFAULTS = {
  calibrationWindows: 3,
  consecutiveBreaches: 2,
  latencyMultiplier: 3,
  socketP95FloorMs: 250,
  restP95FloorMs: 1000,
  onboardingP95FloorMs: 3000,
  errorRateThreshold: 0.05,
  postBreachMs: 60000,
};

function createDetector(options = {}) {
  return {
    options: { ...DEFAULTS, ...options },
    calibration: [],
    baseline: null,
    consecutiveBreachCount: 0,
    breachAt: null,
    breakingPointUsers: null,
    breachSignal: null,
  };
}

function median(values) {
  const sorted = values
    .filter((value) => Number.isFinite(value) && value >= 0)
    .sort((left, right) => left - right);
  if (sorted.length === 0) {
    return 0;
  }

  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0
    ? (sorted[middle - 1] + sorted[middle]) / 2
    : sorted[middle];
}

function calibrate(detector, stats) {
  if (detector.baseline !== null || !stats || stats.activeUsers <= 0) {
    return detector.baseline;
  }

  detector.calibration.push({
    socketP95: stats.socketP95 || 0,
    restP95: stats.restP95 || 0,
    onboardingP95: stats.onboardingP95 || 0,
  });

  if (detector.calibration.length < detector.options.calibrationWindows) {
    return null;
  }

  detector.baseline = {
    socketP95: median(detector.calibration.map((sample) => sample.socketP95)),
    restP95: median(detector.calibration.map((sample) => sample.restP95)),
    onboardingP95: median(detector.calibration.map((sample) => sample.onboardingP95)),
  };
  return detector.baseline;
}

function exceedsLatency(value, baseline, multiplier, floor) {
  if (!Number.isFinite(value) || value <= 0) {
    return false;
  }
  return value >= Math.max(floor, baseline * multiplier);
}

function breachSignal(detector, stats) {
  if ((stats.pingTimeoutIncrement || 0) > 0) return 'ping_timeout';
  if ((stats.serverDisconnectIncrement || 0) > 0) return 'server_disconnect';
  if ((stats.errorRate || 0) >= detector.options.errorRateThreshold) return 'error_rate';

  const baseline = detector.baseline;
  if (exceedsLatency(
    stats.socketP95,
    baseline.socketP95,
    detector.options.latencyMultiplier,
    detector.options.socketP95FloorMs,
  )) return 'socket_p95';
  if (exceedsLatency(
    stats.restP95,
    baseline.restP95,
    detector.options.latencyMultiplier,
    detector.options.restP95FloorMs,
  )) return 'rest_p95';
  if (exceedsLatency(
    stats.onboardingP95,
    baseline.onboardingP95,
    detector.options.latencyMultiplier,
    detector.options.onboardingP95FloorMs,
  )) return 'onboarding_p95';

  return null;
}

function evaluate(detector, stats, now = Date.now()) {
  if (detector.baseline === null) {
    return { state: 'calibrating' };
  }

  if (detector.breachAt !== null) {
    return {
      state: 'post_observe',
      shouldStop: now - detector.breachAt >= detector.options.postBreachMs,
      breakingPointUsers: detector.breakingPointUsers,
      breachSignal: detector.breachSignal,
    };
  }

  const signal = breachSignal(detector, stats);
  if (signal === null) {
    detector.consecutiveBreachCount = 0;
    return { state: 'monitoring', shouldStop: false };
  }

  detector.consecutiveBreachCount += 1;
  if (detector.consecutiveBreachCount < detector.options.consecutiveBreaches) {
    return { state: 'monitoring', shouldStop: false, pendingSignal: signal };
  }

  detector.breachAt = now;
  detector.breakingPointUsers = stats.activeUsers;
  detector.breachSignal = signal;

  return {
    state: 'breached',
    shouldStop: false,
    breakingPointUsers: detector.breakingPointUsers,
    breachSignal: detector.breachSignal,
  };
}

module.exports = {
  createDetector,
  calibrate,
  evaluate,
};
