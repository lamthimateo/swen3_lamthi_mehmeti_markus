import React, { useEffect, useState } from 'react';
import './DocumentDetail.css';

export default function DocumentDetail() {
  const documentId = window.location.pathname.split('/').pop();
  const [document, setDocument] = useState(null);
  const [labels, setLabels] = useState([]);
  const [allLabels, setAllLabels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedLabel, setSelectedLabel] = useState('');
  const [selectedWeight, setSelectedWeight] = useState(1);
  const [attachingLabel, setAttachingLabel] = useState(false);

  // Fetch document details
  const fetchDocument = async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}`);
      if (!response.ok) throw new Error('Document not found');
      const data = await response.json();
      setDocument(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch document labels
  const fetchLabels = async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}/labels`);
      if (!response.ok) throw new Error('Failed to fetch labels');
      const data = await response.json();
      setLabels(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch all available labels
  const fetchAllLabels = async () => {
    try {
      const response = await fetch('/api/labels');
      if (!response.ok) throw new Error('Failed to fetch labels');
      const data = await response.json();
      setAllLabels(data);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    Promise.all([fetchDocument(), fetchLabels(), fetchAllLabels()])
      .finally(() => setLoading(false));
  }, [documentId]);

  // Attach a label to the document
  const handleAttachLabel = async () => {
    if (!selectedLabel) {
      setError('Please select a label');
      return;
    }

    setAttachingLabel(true);
    try {
      const response = await fetch(
        `/api/documents/${documentId}/labels?labelId=${selectedLabel}&weight=${selectedWeight}`,
        { method: 'POST' }
      );

      if (!response.ok) {
        const statusText = response.statusText;
        if (response.status === 409) {
          throw new Error('This label is already attached to the document');
        }
        throw new Error(`Failed to attach label (${response.status})`);
      }

      // Refresh labels
      await fetchLabels();
      setSelectedLabel('');
      setSelectedWeight(1);
    } catch (err) {
      setError(err.message);
    } finally {
      setAttachingLabel(false);
    }
  };

  // Detach a label from the document
  const handleDetachLabel = async (labelId) => {
    try {
      const response = await fetch(
        `/api/documents/${documentId}/labels/${labelId}`,
        { method: 'DELETE' }
      );

      if (!response.ok) {
        throw new Error(`Failed to detach label (${response.status})`);
      }

      // Refresh labels
      await fetchLabels();
    } catch (err) {
      setError(err.message);
    }
  };

  const goBack = () => {
    window.location.href = '/';
  };

  if (loading) return <div className="loading">Loading document...</div>;

  if (!document) {
    return (
      <div className="document-detail">
        <div className="error-message">{error || 'Document not found'}</div>
        <button onClick={goBack} className="btn btn-secondary">← Back</button>
      </div>
    );
  }

  // Filter out already attached labels
  const availableLabels = allLabels.filter(
    label => !labels.some(l => l.label.id === label.id)
  );

  return (
    <div className="document-detail">
      <header className="detail-header">
        <button onClick={goBack} className="btn btn-secondary">← Back</button>
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

        {/* Current Labels */}
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
                    <span className="label-weight">Weight: {docLabel.weight}</span>
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

        {/* Attach New Label */}
        <div className="attach-label">
          <h3>Attach New Label</h3>
          {availableLabels.length === 0 ? (
            <p className="no-labels">All labels are already attached</p>
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
                  onChange={(e) => setSelectedWeight(parseFloat(e.target.value))}
                />
              </div>

              <button
                onClick={handleAttachLabel}
                disabled={attachingLabel || !selectedLabel}
                className="btn btn-primary"
              >
                {attachingLabel ? 'Attaching...' : 'Attach Label'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}