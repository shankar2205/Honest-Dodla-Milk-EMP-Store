type PageQueryResult<T> = {
  data: T[] | null;
  error: { message: string } | null;
};

const PAGE_SIZE = 500;

/**
 * Fetch every row in stable, ordered pages so report totals and exports
 * don't silently stop at the Supabase API row limit.
 */
export async function fetchAllPages<T>(
  fetchPage: (from: number, to: number) => PromiseLike<PageQueryResult<T>>
): Promise<PageQueryResult<T>> {
  const rows: T[] = [];

  for (let from = 0; ; from += PAGE_SIZE) {
    const { data, error } = await fetchPage(from, from + PAGE_SIZE - 1);
    if (error) return { data: null, error };

    const page = data ?? [];
    rows.push(...page);
    if (page.length < PAGE_SIZE) return { data: rows, error: null };
  }
}
