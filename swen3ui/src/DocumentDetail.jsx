import React, { useEffect, useState } from "react";
import "./DocumentDetail.css";

const MAX_LABEL_NAME_LENGTH = 50;

export default function DocumentDetail() {
  const documentId = window.location.pathname.split("/").pop();
  const [document, setDocument] = useState(null);
  const [labels, setLabels] = useState([]);
  const [allLabels, setAllLabels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // attach form
  const [selectedLabel, setSelectedLabel] = useState("");
  const [selectedWeight, setSelectedWeight] = useState(1);
  const [attachingLabel, setAttachingLabel] = useState(false);

  // create form — kept in its own state so a failed create does not
  // wipe out (or get wiped out by) an unrelated page-level error
  const [newLabelName, setNewLabelName] = useState("");
  const [creatingLabel, setCreatingLabel] = useState(false);
  const [createError, setCreateError] = useState(null);

  // Fetch document details
  const fetchDocument = async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}`);
      if (!response.ok) throw new Error("Document not found");
      const data = await response.json();
      setDocument(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch the labels attached to this document (ordered by weight, highest first)
  const fetchLabels = async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}/labels`);
      if (!response.ok) throw new Error("Failed to fetch labels");
      const data = await response.json();
      setLabels(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch every label that exists in the system (not paginated)
  const fetchAllLabels = async () => {
    try {
      const response = await fetch("/api/labels");
      if (!response.ok) throw new Error("Failed to fetch labels");
      const data = await response.json();
      setAllLabels(data);
      return data;
    } catch (err) {
      setError(err.message);
      return [];
    }
  };

  useEffect(() => {
    Promise.all([fetchDocument(), fetchLabels(), fetchAllLabels()]).finally(
      () => setLoading(false),
    );
  }, [documentId]);

  // --- Validation -----------------------------------------------------------
  // Checked client-side so the user gets feedback without a round trip; the
  // server enforces the same rules (400 blank, 409 duplicate) and we map those
  // status codes below, because the API returns no message text.
  const validateLabelName = (value) => {
    const trimmed = value.trim();
    if (!trimmed) {
      return "Label name is required";
    }
    if (trimmed.length > MAX_LABEL_NAME_LENGTH) {
      return `Label name must be ${MAX_LABEL_NAME_LENGTH} characters or fewer`;
    }
    if (allLabels.some((l) => l.name.toLowerCase() === trimmed.toLowerCase())) {
      return "A label with that name already exists";
    }
    return null;
  };

  const nameValidationError = newLabelName
    ? validateLabelName(newLabelName)
    : null;

  // Create a brand new label
  const handleCreateLabel = async (e) => {
    e.preventDefault();

    const validationError = validateLabelName(newLabelName);
    if (validationError) {
      setCreateError(validationError);
      return;
    }

    const trimmed = newLabelName.trim();
    setCreatingLabel(true);
    setCreateError(null);

    try {
      // encodeURIComponent matters here: the endpoint takes the name as a query
      // parameter, so a name containing "&", "#" or a space would otherwise break it
      const response = await fetch(
        `/api/labels?name=${encodeURIComponent(trimmed)}`,
        { method: "POST" },
      );

      if (!response.ok) {
        if (response.status === 400)
          throw new Error("Label name must not be blank");
        if (response.status === 409)
          throw new Error("A label with that name already exists");
        throw new Error(`Could not create label (${response.status})`);
      }

      const created = await response.json();
      await fetchAllLabels();
      setNewLabelName("");
      // pre-select the new label so attaching it is a single click
      setSelectedLabel(String(created.id));
    } catch (err) {
      setCreateError(err.message);
    } finally {
      setCreatingLabel(false);
    }
  };

  // Attach an existing label to this document
  const handleAttachLabel = async () => {
    if (!selectedLabel) {
      setError("Please select a label");
      return;
    }

    // guard against an empty or non-numeric weight field becoming NaN in the URL
    const weight =
      Number.isFinite(selectedWeight) && selectedWeight >= 0
        ? selectedWeight
        : 1;

    setAttachingLabel(true);
    setError(null);

    try {
      const response = await fetch(
        `/api/documents/${documentId}/labels?labelId=${selectedLabel}&weight=${weight}`,
        { method: "POST" },
      );

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error("Document or label no longer exists");
        }
        if (response.status === 409) {
          throw new Error("This label is already attached to the document");
        }
        throw new Error(`Failed to attach label (${response.status})`);
      }

      await fetchLabels();
      setSelectedLabel("");
      setSelectedWeight(1);
    } catch (err) {
      setError(err.message);
    } finally {
      setAttachingLabel(false);
    }
  };

  // Detach a label from this document (the label itself keeps existing)
  const handleDetachLabel = async (labelId) => {
    try {
      const response = await fetch(
        `/api/documents/${documentId}/labels/${labelId}`,
        { method: "DELETE" },
      );

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error("That label was not attached to this document");
        }
        throw new Error(`Failed to detach label (${response.status})`);
      }

      setError(null);
      await fetchLabels();
    } catch (err) {
      setError(err.message);
    }
  };

  const goBack = () => {
    window.location.href = "/";
  };

  if (loading) return <div className="loading">Loading document...</div>;

  if (!document) {
    return (
      <div className="document-detail">
        <div className="error-message">{error || "Document not found"}</div>
        <button onClick={goBack} className="btn btn-secondary">
          ← Back
        </button>
      </div>
    );
  }

  // Labels that exist but are not yet on this document
  const availableLabels = allLabels.filter(
    (label) => !labels.some((l) => l.label.id === label.id),
  );

  return (
    <div className="document-detail">
      <header className="detail-header">
        <button onClick={goBack} className="btn btn-secondary">
          ← Back
        </button>
        <h1>{document.filename}</h1>
      </header>

      {error && <div className="error-message">{error}</div>}

      {/* Document Info */}
      <div className="document-info">
        <div className="info-field">
          <label>ID:</label>
          <span>{document.id}</span>
        </div>
        <div className="info-field">
          <label>Upload Date:</label>
          <span>{new Date(document.uploadDate).toLocaleString()}</span>
        </div>
        {document.storagePath && (
          <div className="info-field">
            <label>Storage Path:</label>
            <span>{document.storagePath}</span>
          </div>
        )}
        {document.summary && (
          <div className="info-field">
            <label>Summary:</label>
            <span>{document.summary}</span>
          </div>
        )}
      </div>

      {/* Labels Section */}
      <div className="labels-section">
        <h2>Labels</h2>

        {/* Currently attached labels */}
        <div className="current-labels">
          <h3>Attached Labels</h3>
          {labels.length === 0 ? (
            <p className="no-labels">No labels attached yet</p>
          ) : (
            <ul className="labels-list">
              {labels.map((docLabel) => (
                <li key={docLabel.label.id} className="label-item">
                  <div className="label-info">
                    <span className="label-name">{docLabel.label.name}</span>
                    <span className="label-weight">
                      Weight: {docLabel.weight}
                    </span>
                  </div>
                  <button
                    onClick={() => handleDetachLabel(docLabel.label.id)}
                    className="btn btn-danger-small"
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Create a new label */}
        <div className="create-label">
          <h3>Create New Label</h3>
          <form className="create-form" onSubmit={handleCreateLabel} noValidate>
            <div className="form-group">
              <label htmlFor="new-label-name">Name:</label>
              <input
                id="new-label-name"
                type="text"
                value={newLabelName}
                maxLength={MAX_LABEL_NAME_LENGTH}
                placeholder="e.g. Invoice"
                onChange={(e) => {
                  setNewLabelName(e.target.value);
                  setCreateError(null);
                }}
                aria-invalid={Boolean(nameValidationError)}
                aria-describedby={
                  nameValidationError ? "new-label-error" : undefined
                }
              />
            </div>

            {(createError || nameValidationError) && (
              <p className="field-error" id="new-label-error">
                {createError || nameValidationError}
              </p>
            )}

            <button
              type="submit"
              disabled={
                creatingLabel ||
                !newLabelName.trim() ||
                Boolean(nameValidationError)
              }
              className="btn btn-primary"
            >
              {creatingLabel ? "Creating..." : "Create Label"}
            </button>
          </form>
        </div>

        {/* Attach an existing label */}
        <div className="attach-label">
          <h3>Attach Existing Label</h3>
          {allLabels.length === 0 ? (
            <p className="no-labels">
              No labels exist yet — create one above, then attach it here.
            </p>
          ) : availableLabels.length === 0 ? (
            <p className="no-labels">
              Every existing label is already attached to this document.
            </p>
          ) : (
            <div className="attach-form">
              <div className="form-group">
                <label htmlFor="label-select">Label:</label>
                <select
                  id="label-select"
                  value={selectedLabel}
                  onChange={(e) => setSelectedLabel(e.target.value)}
                >
                  <option value="">-- Select a label --</option>
                  {availableLabels.map((label) => (
                    <option key={label.id} value={label.id}>
                      {label.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="weight-input">Weight:</label>
                <input
                  id="weight-input"
                  type="number"
                  min="0"
                  step="0.1"
                  value={selectedWeight}
                  onChange={(e) => {
                    const raw = e.target.value;
                    setSelectedWeight(raw === "" ? "" : parseFloat(raw));
                  }}
                />
              </div>

              <button
                onClick={handleAttachLabel}
                disabled={attachingLabel || !selectedLabel}
                className="btn btn-primary"
              >
                {attachingLabel ? "Attaching..." : "Attach Label"}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
