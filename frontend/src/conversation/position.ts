import type { Coordinates } from './rules';

/** Why the position couldn't be read: the permission denied, or no position to be had. */
export class PositionError extends Error
{
  constructor(readonly reason: 'denied' | 'unavailable')
  {
    super(`The position is ${reason}.`);
    this.name = 'PositionError';
  }
}

/**
 * The device's position, asked only when an action needs it (CLAUDE.md, frontend point 6): never kept, never saved; a
 * fresh one every time.
 */
export function currentPosition(): Promise<Coordinates>
{
  return new Promise((resolve, reject) =>
  {
    if (!('geolocation' in navigator))
    {
      reject(new PositionError('unavailable'));

      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      (failure) => reject(new PositionError(failure.code === failure.PERMISSION_DENIED ? 'denied' : 'unavailable')),
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 0 },
    );
  });
}
