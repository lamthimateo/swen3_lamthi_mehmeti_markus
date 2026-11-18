import { useEffect, useState } from "react";
import { errorMessage, log } from "./api";
import SearchBar from "./SearchBar";
import "./Dashboard.css";

/** Results page for /search?q=term: lists the documents whose OCR text (or filename) matches. */
export default function Search() {
  const query = (new URLSearchParams(window.location.search).get("q") ?? "").trim();

  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(query !== "");
  const [error, setError] = useState(null);

  useEffect(() => {
    if (query === "") {
      return undefined;
    }
    let cancelled = false;

    (async () => {
      try {
        const params = new URLSearchParams({ q: query });
        const response = await fetch(`/api/documents/search?${params}`);
        if (!response.ok) {
          throw new Error(await errorMessage(response, "Search failed"));
        }
        const data = await response.json();
        if (!cancelled) {
          log.info(`search "${query}" returned ${data.length} document(s)`);
          setResults(data);
        }
      } catch (err) {
        log.error(`search "${query}" failed`, err);
        if (!cancelled) {
          setError(err.message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    // ignore the answer if the page is left (or React re-runs the effect) before it arrives
    return () => {
      cancelled = true;
    };
  }, [query]);

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>
          <a href="/" style={{ color: "inherit", textDecoration: "none" }}>
            Document Manager
          </a>
        </h1>
        <SearchBar initialQuery={query} />
      </header>

      <div className="documents-section">
        <h2>Search results</h2>

        {query === "" ? (
          <div className="no-documents">Enter a search term above.</div>
        ) : loading ? (
          <div className="loading">Searching...</div>
        ) : error ? (
          <div className="error-message">{error}</div>
        ) : results.length === 0 ? (
          <div className="no-documents">
            No documents match &ldquo;{query}&rdquo;. Documents become searchable
            a few moments after the upload, once their text has been recognised.
          </div>
        ) : (
          <>
            <p className="search-summary">
              {results.length} document{results.length === 1 ? "" : "s"} match
              &ldquo;{query}&rdquo;
            </p>
            <table className="documents-table">
              <thead>
                <tr>
                  <th>Filename</th>
                  <th>Upload Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {results.map((doc) => (
                  <tr key={doc.id}>
                    <td>{doc.filename}</td>
                    <td>{new Date(doc.uploadDate).toLocaleString()}</td>
                    <td>
                      <a href={`/document/${doc.id}`} className="btn btn-primary">
                        View
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </div>
  );
}
