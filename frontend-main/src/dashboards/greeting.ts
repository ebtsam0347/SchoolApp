export function greetingForHour(hour: number): string {
  if (hour < 5) return 'Good evening';
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

export function greetingForNow(date: Date = new Date()): string {
  return greetingForHour(date.getHours());
}

export function welcomeTitle(fullName: string | undefined | null, date?: Date): string {
  const greeting = greetingForNow(date);
  const name = fullName?.trim();
  return name ? `${greeting}, ${name}` : greeting;
}
