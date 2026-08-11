const { createRing, addSample, addError, snapshot } = require('../breakpoint/windowed-metrics');

describe('windowed metrics', () => {
  test('calculates nearest-rank percentiles and error rate', () => {
    const ring = createRing(5000);
    [10, 20, 30, 40, 50].forEach((value) => addSample(ring, value, 1000));
    addError(ring, 1000);

    expect(snapshot(ring, 2000)).toEqual({
      count: 5,
      errorCount: 1,
      errorRate: 1 / 6,
      p50: 30,
      p95: 50,
      p99: 50,
    });
  });

  test('drops samples and errors outside the window', () => {
    const ring = createRing(5000);
    addSample(ring, 10, 1000);
    addError(ring, 1000);
    addSample(ring, 20, 6000);

    expect(snapshot(ring, 6000)).toEqual({
      count: 1,
      errorCount: 0,
      errorRate: 0,
      p50: 20,
      p95: 20,
      p99: 20,
    });
  });
});
