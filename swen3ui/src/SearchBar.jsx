import "./SearchBar.css";

/**
 * Search box shown on the dashboard and on the results page. A plain GET form: submitting it opens
 * /search?q=..., which the results page reads, so no client-side routing is needed.
 */
export default function SearchBar({ initialQuery = "" }) {
  return (
    <form className="search-bar" action="/search" method="get" role="search">
      <input
        type="search"
        name="q"
        className="search-input"
        placeholder="Search in document contents..."
        aria-label="Search documents"
        defaultValue={initialQuery}
        maxLength={200}
        required
      />
      <button type="submit" className="btn btn-primary">
        Search
      </button>
    </form>
  );
}
