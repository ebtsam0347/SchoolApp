// Shared people-display helpers. Keeps province codes and country codes
// from leaking to user-facing copy.

const PROVINCE_NAME: Record<string, string> = {
  PB: 'Punjab',
  SD: 'Sindh',
  KP: 'Khyber Pakhtunkhwa',
  BL: 'Balochistan',
  ICT: 'Islamabad',
  AJK: 'Azad Jammu & Kashmir',
  GB: 'Gilgit-Baltistan',
};

/** "Sialkot, Punjab" — falls back to the raw code if unknown. */
export function formatCityWithProvince(
  cityName: string | undefined | null,
  provinceCode: string | undefined | null,
): string {
  if (!cityName) return '—';
  if (!provinceCode) return cityName;
  const province = PROVINCE_NAME[provinceCode.toUpperCase()] ?? provinceCode;
  return `${cityName}, ${province}`;
}

const COUNTRY_NAME: Record<string, string> = {
  PK: 'Pakistan',
};

/** "Pakistan" — falls back to the raw code if unknown. */
export function formatCountry(code: string | undefined | null): string {
  if (!code) return '—';
  return COUNTRY_NAME[code.toUpperCase()] ?? code;
}
