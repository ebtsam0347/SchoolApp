// Spring-style page envelope. OpenAPI has no generics, so the generated specs
// expose monomorphized PageResponseXxx schemas instead — this is the FE's one
// generic wrapper. Domain modules alias it (attendance: PageResponse / BffPage).
//
// content + counts are always present on a Spring page; sort is null when the
// query is unsorted. (people/types.ts keeps its own all-optional variant per
// that module's "every wire field is optional" convention.)
export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string | null;
};
