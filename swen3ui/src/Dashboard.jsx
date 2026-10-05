import React, { useEffect, useState } from "react";
import "./Dashboard.css";

export default function Dashboard() {
  const [documents, setDocuments] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sortField, setSortField] = useState("uploadDate");
  const [sortDir, setSortDir] = useState("desc");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [uploadError, setUploadError] = useState(null);
  const [uploading, setUploading] = useState(false);

  // Fetch documents with pagination
  const fetchDocuments = async (pageNum = 0) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        page: pageNum,
        size: pageSize,
        sort: `${sortField},${sortDir}`,
      });
      const response = await fetch(`/api/documents?${params}`);

      if (!response.ok) {
        if (response.status === 400) {
          throw new Error("Invalid sort field or pagination parameters");
        }
        throw new Error(`Failed to fetch documents (${response.status})`);
      }

      const data = await response.json();
      setDocuments(data.content || []);
      setTotalPages(data.totalPages || 1);
      setPage(pageNum);
    } catch (err) {
      setError(err.message);
      setDocuments([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments(0);
  }, [pageSize, sortField, sortDir]);

  // Handle file upload
  const handleUpload = async (e) => {
    const file = e.target.files?.[0];

    if (!file) {
      setUploadError("Please select a file");
      return;
    }

    if (file.size === 0) {
      setUploadError("File is empty");
      return;
    }

    const formData = new FormData();
    formData.append("file", file);

    setUploading(true);
    setUploadError(null);

    try {
      const response = await fetch("/api/documents", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        if (response.status === 400) {
          throw new Error("File is empty or invalid");
        }
        throw new Error(`Upload failed (${response.status})`);
      }

      // Refresh the document list
      fetchDocuments(0);
      e.target.value = ""; // Reset file input
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploading(false);
    }
  };

  // Handle document deletion
  const handleDelete = async (id) => {
    if (!confirm("Are you sure you want to delete this document?")) {
      return;
    }

    try {
      const response = await fetch(`/api/documents/${id}`, {
        method: "DELETE",
      });

      if (!response.ok) {
        throw new Error(`Delete failed (${response.status})`);
      }

      // Refresh the list
      fetchDocuments(page);
    } catch (err) {
      setError(err.message);
    }
  };

  // Navigate to document detail
  const goToDocument = (id) => {
    window.location.href = `/document/${id}`;
  };

  const handleSortChange = (field) => {
    if (sortField === field) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortField(field);
      setSortDir("desc");
    }
  };

  const handlePrevious = () => {
    if (page > 0) {
      fetchDocuments(page - 1);
    }
  };

  const handleNext = () => {
    if (page + 1 < totalPages) {
      fetchDocuments(page + 1);
    }
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>Document Manager</h1>
      </header>

      {/* Upload Section */}
      <div className="upload-section">
        <label htmlFor="file-input" className="upload-label">
          📁 Upload Document
        </label>
        <input
          id="file-input"
          type="file"
          onChange={handleUpload}
          disabled={uploading}
          className="file-input"
        />
        {uploading && <span className="upload-status">Uploading...</span>}
        {uploadError && <div className="error-message">{uploadError}</div>}
      </div>

      {/* Error Message */}
      {error && <div className="error-message">{error}</div>}

      {/* Document List */}
      <div className="documents-section">
        <h2>Documents</h2>
        {loading ? (
          <div className="loading">Loading documents...</div>
        ) : documents.length === 0 ? (
          <div className="no-documents">
            No documents found. Upload one to get started!
          </div>
        ) : (
          <>
            <table className="documents-table">
              <thead>
                <tr>
                  <th onClick={() => handleSortChange("filename")}>
                    Filename{" "}
                    {sortField === "filename" &&
                      (sortDir === "asc" ? "↑" : "↓")}
                  </th>
                  <th onClick={() => handleSortChange("uploadDate")}>
                    Upload Date{" "}
                    {sortField === "uploadDate" &&
                      (sortDir === "asc" ? "↑" : "↓")}
                  </th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id}>
                    <td
                      className="filename-cell"
                      onClick={() => goToDocument(doc.id)}
                      style={{
                        cursor: "pointer",
                        color: "#0066cc",
                        textDecoration: "underline",
                      }}
                    >
                      {doc.filename}
                    </td>
                    <td>{new Date(doc.uploadDate).toLocaleString()}</td>
                    <td>
                      <button
                        onClick={() => goToDocument(doc.id)}
                        className="btn btn-primary"
                      >
                        View
                      </button>
                      <button
                        onClick={() => handleDelete(doc.id)}
                        className="btn btn-danger"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination Controls */}
            <div className="pagination-controls">
              <button
                onClick={handlePrevious}
                disabled={page === 0}
                className="btn btn-secondary"
              >
                ← Previous
              </button>
              <span className="page-info">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={handleNext}
                disabled={page + 1 >= totalPages}
                className="btn btn-secondary"
              >
                Next →
              </button>
              <select
                value={pageSize}
                onChange={(e) => setPageSize(Number(e.target.value))}
                className="page-size-select"
              >
                <option value={5}>5 per page</option>
                <option value={10}>10 per page</option>
                <option value={20}>20 per page</option>
                <option value={50}>50 per page</option>
                <option value={100}>100 per page</option>
              </select>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
