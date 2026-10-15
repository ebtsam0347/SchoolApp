// Country → currency catalog for International travel requests. No
// countries/currency catalog API exists yet — kept as a static list here;
// swap for a real catalogsApi.countries() once one ships.

export type CountryCurrency = {
  country: string;
  currency: string;
  symbol: string;
};

export const COUNTRY_CURRENCIES: CountryCurrency[] = [
  { country: 'Pakistan', currency: 'PKR', symbol: 'PKR' },
  { country: 'United States', currency: 'USD', symbol: '$' },
  { country: 'United Kingdom', currency: 'GBP', symbol: '£' },
  { country: 'Germany', currency: 'EUR', symbol: '€' },
  { country: 'France', currency: 'EUR', symbol: '€' },
  { country: 'Italy', currency: 'EUR', symbol: '€' },
  { country: 'Spain', currency: 'EUR', symbol: '€' },
  { country: 'Netherlands', currency: 'EUR', symbol: '€' },
  { country: 'Belgium', currency: 'EUR', symbol: '€' },
  { country: 'Portugal', currency: 'EUR', symbol: '€' },
  { country: 'Ireland', currency: 'EUR', symbol: '€' },
  { country: 'Switzerland', currency: 'CHF', symbol: 'CHF' },
  { country: 'Sweden', currency: 'SEK', symbol: 'kr' },
  { country: 'Norway', currency: 'NOK', symbol: 'kr' },
  { country: 'Denmark', currency: 'DKK', symbol: 'kr' },
  { country: 'Poland', currency: 'PLN', symbol: 'zł' },
  { country: 'Turkey', currency: 'TRY', symbol: '₺' },
  { country: 'Russia', currency: 'RUB', symbol: '₽' },
  { country: 'China', currency: 'CNY', symbol: '¥' },
  { country: 'Japan', currency: 'JPY', symbol: '¥' },
  { country: 'South Korea', currency: 'KRW', symbol: '₩' },
  { country: 'India', currency: 'INR', symbol: '₹' },
  { country: 'Bangladesh', currency: 'BDT', symbol: '৳' },
  { country: 'Sri Lanka', currency: 'LKR', symbol: 'Rs' },
  { country: 'Nepal', currency: 'NPR', symbol: 'Rs' },
  { country: 'Afghanistan', currency: 'AFN', symbol: '؋' },
  { country: 'Iran', currency: 'IRR', symbol: '﷼' },
  { country: 'Iraq', currency: 'IQD', symbol: 'ID' },
  { country: 'Saudi Arabia', currency: 'SAR', symbol: 'SR' },
  { country: 'United Arab Emirates', currency: 'AED', symbol: 'AED' },
  { country: 'Qatar', currency: 'QAR', symbol: 'QR' },
  { country: 'Kuwait', currency: 'KWD', symbol: 'KD' },
  { country: 'Bahrain', currency: 'BHD', symbol: 'BD' },
  { country: 'Oman', currency: 'OMR', symbol: 'OMR' },
  { country: 'Jordan', currency: 'JOD', symbol: 'JD' },
  { country: 'Lebanon', currency: 'LBP', symbol: 'LL' },
  { country: 'Egypt', currency: 'EGP', symbol: 'E£' },
  { country: 'Israel', currency: 'ILS', symbol: '₪' },
  { country: 'South Africa', currency: 'ZAR', symbol: 'R' },
  { country: 'Nigeria', currency: 'NGN', symbol: '₦' },
  { country: 'Kenya', currency: 'KES', symbol: 'KSh' },
  { country: 'Malaysia', currency: 'MYR', symbol: 'RM' },
  { country: 'Singapore', currency: 'SGD', symbol: 'S$' },
  { country: 'Indonesia', currency: 'IDR', symbol: 'Rp' },
  { country: 'Thailand', currency: 'THB', symbol: '฿' },
  { country: 'Vietnam', currency: 'VND', symbol: '₫' },
  { country: 'Philippines', currency: 'PHP', symbol: '₱' },
  { country: 'Australia', currency: 'AUD', symbol: 'A$' },
  { country: 'New Zealand', currency: 'NZD', symbol: 'NZ$' },
  { country: 'Canada', currency: 'CAD', symbol: 'C$' },
  { country: 'Brazil', currency: 'BRL', symbol: 'R$' },
  { country: 'Mexico', currency: 'MXN', symbol: 'Mex$' },
  { country: 'Argentina', currency: 'ARS', symbol: 'AR$' },
];

export const COUNTRY_OPTIONS = COUNTRY_CURRENCIES.map((c) => ({
  value: c.country,
  label: c.country,
}));

export const CURRENCY_OPTIONS = Array.from(
  new Map(COUNTRY_CURRENCIES.map((c) => [c.currency, c])).values(),
).map((c) => ({
  value: c.currency,
  label: `${c.symbol} — ${c.currency}`,
}));

export function currencyForCountry(country?: string): string | undefined {
  return COUNTRY_CURRENCIES.find((c) => c.country === country)?.currency;
}
