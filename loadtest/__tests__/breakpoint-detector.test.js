const { createDetector, calibrate, evaluate } = require('../breakpoint/breakpoint-detector');

const stableWindow = (overrides = {}) => ({
  activeUsers: 10,
  socketP95: 20,
  restP95: 100,
  onboardingP95: 200,
  errorRate: 0,
  pingTimeoutIncrement: 0,
  serverDisconnectIncrement: 0,
  ...overrides,
});

describe('breakpoint detector', () => {
  test('calibrates a median baseline after the configured number of windows', () => {
    const detector = createDetector({ calibrationWindows: 3 });

    expect(calibrate(detector, stableWindow({ socketP95: 10 }))).toBeNull();
    expect(calibrate(detector, stableWindow({ socketP95: 30 }))).toBeNull();
    expect(calibrate(detector, stableWindow({ socketP95: 20 }))).toEqual({
      socketP95: 20,
      restP95: 100,
      onboardingP95: 200,
    });
  });

  test('requires consecutive breaches and then observes before stopping', () => {
    const detector = createDetector({
      calibrationWindows: 1,
      consecutiveBreaches: 2,
      postBreachMs: 1000,
    });
    calibrate(detector, stableWindow());
    const unhealthy = stableWindow({ activeUsers: 40, errorRate: 0.1 });

    expect(evaluate(detector, unhealthy, 1000)).toMatchObject({
      state: 'monitoring',
      pendingSignal: 'error_rate',
    });
    expect(evaluate(detector, unhealthy, 2000)).toMatchObject({
      state: 'breached',
      breakingPointUsers: 40,
      breachSignal: 'error_rate',
    });
    expect(evaluate(detector, stableWindow(), 2500)).toMatchObject({
      state: 'post_observe',
      shouldStop: false,
    });
    expect(evaluate(detector, stableWindow(), 3000)).toMatchObject({
      state: 'post_observe',
      shouldStop: true,
    });
  });
});
