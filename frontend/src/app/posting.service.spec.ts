import { newReference } from './posting.service';

/**
 * The fallback path only runs outside a secure context, so it never runs during development.
 * Code that never runs locally is exactly the code that needs a test.
 */
describe('newReference', () => {
  const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

  it('returns a v4 UUID when crypto.randomUUID is available', () => {
    expect(newReference()).toMatch(UUID_V4);
  });

  it('returns a v4 UUID from the fallback when crypto.randomUUID is missing', () => {
    const original = crypto.randomUUID;
    (crypto as { randomUUID?: unknown }).randomUUID = undefined;

    try {
      expect(newReference()).toMatch(UUID_V4);
    } finally {
      (crypto as { randomUUID?: unknown }).randomUUID = original;
    }
  });

  it('does not repeat itself', () => {
    expect(newReference()).not.toEqual(newReference());
  });
});
