import React, { useState, useEffect, useRef } from 'react';
import '../styles/SearchForm.css';

// ── tiny helpers ──────────────────────────────────────────────
function fuzzy(str, query) {
  if (!str || !query) return false;
  try {
    const regex = new RegExp(query.trim(), 'ui');
    return regex.test(str);
  } catch {
    return false;
  }
}

function DropdownField({ label, placeholder, value, onChange, onSelect, items, open, onOpen, disabled, loading, filled }) {
  const ref = useRef(null);
  const filtered = value ? items.filter(i => fuzzy(i.label, value)) : items;

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onOpen]);

  return (
    <div className={`field ${filled ? 'filled' : ''} ${!disabled && open ? 'active' : ''}`}>
      <label>{label}</label>
      <div className="dropdown-wrap" ref={ref}>
        <input
          type="text"
          className={`field-input${filled ? ' has-value' : ''}${loading ? ' loading-state' : ''}`}
          placeholder={loading ? 'Loading…' : placeholder}
          value={value}
          onChange={e => { onChange(e.target.value); if (!open) onOpen(true); }}
          onFocus={() => !disabled && onOpen(true)}
          disabled={disabled}
          autoComplete="off"
        />
        {open && !disabled && (
          <div className="dropdown-list">
            {filtered.length === 0
              ? <div className="dropdown-empty">No results</div>
              : filtered.map(item => (
                <div
                  key={item.value ?? item.label}
                  className="dropdown-item"
                  onMouseDown={e => { e.preventDefault(); onSelect(item); onOpen(false); }}
                >
                  {item.label}
                </div>
              ))
            }
          </div>
        )}
      </div>
    </div>
  );
}

function TextField({ label, placeholder, value, onChange, onBlur, disabled, filled }) {
  return (
    <div className={`field ${filled ? 'filled' : ''}`}>
      <label>{label}</label>
      <input
        type="text"
        className={`field-input${filled ? ' has-value' : ''}`}
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        onBlur={onBlur}
        disabled={disabled}
        autoComplete="off"
      />
    </div>
  );
}

// ── main component ────────────────────────────────────────────
export default function SearchForm({ onSearch, loading }) {
  // data lists
  const [states,         setStates]         = useState([]);
  const [municipalities, setMunicipalities] = useState([]);
  const [streets,        setStreets]        = useState([]);
  const [areas,          setAreas]          = useState([]);

  // loading flags per-field
  const [loadingStates,  setLoadingStates]  = useState(true);
  const [loadingMunis,   setLoadingMunis]   = useState(false);
  const [loadingStreets, setLoadingStreets] = useState(false);
  const [loadingAreas,   setLoadingAreas]   = useState(false);

  // selected values
  const [selectedState,        setSelectedState]        = useState(null);
  const [selectedMunicipality, setSelectedMunicipality] = useState(null);
  const [selectedStreet,       setSelectedStreet]       = useState(null);
  const [selectedArea,         setSelectedArea]         = useState(null);

  // text inputs (typed / displayed)
  const [stateInput,        setStateInput]        = useState('');
  const [municipalityInput, setMunicipalityInput] = useState('');
  const [postalCode,        setPostalCode]        = useState('');
  const [streetInput,       setStreetInput]       = useState('');
  const [areaInput,         setAreaInput]         = useState('');
  const [streetNumber,      setStreetNumber]      = useState('');

  // open dropdown
  const [open, setOpen] = useState(null);

  // ── fetch states on mount ─────────────────────────────────
  useEffect(() => {
    setLoadingStates(true);
    fetch('http://localhost:8080/api/address/states')
      .then(r => r.json())
      .then(d => { setStates(d.items || []); })
      .catch(console.error)
      .finally(() => setLoadingStates(false));
  }, []);

  // ── handlers ─────────────────────────────────────────────
  const handleStateSelect = async (state) => {
    setSelectedState(state);
    setStateInput(state.label);
    // reset downstream
    setSelectedMunicipality(null); setMunicipalityInput('');
    setPostalCode('');
    setSelectedStreet(null); setStreetInput('');
    setSelectedArea(null); setAreaInput('');
    setStreetNumber('');
    setMunicipalities([]); setStreets([]); setAreas([]);

    setLoadingMunis(true);
    try {
      const r = await fetch('http://localhost:8080/api/address/municipalities', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(state),
      });
      const d = await r.json();
      const items = d.items || [];
      setMunicipalities(items);
      if (items.length === 1) {
        setSelectedMunicipality(items[0]);
        setMunicipalityInput(items[0].label);
      }
    } catch (e) { console.error(e); }
    finally { setLoadingMunis(false); }
  };

  const handleMunicipalitySelect = (muni) => {
    setSelectedMunicipality(muni);
    setMunicipalityInput(muni.label);
    // reset downstream
    setPostalCode('');
    setSelectedStreet(null); setStreetInput('');
    setSelectedArea(null); setAreaInput('');
    setStreetNumber('');
    setStreets([]); setAreas([]);
  };

  const handlePostalBlur = async () => {
    if (!postalCode || !selectedState || !selectedMunicipality) return;
    setStreets([]);
    setSelectedStreet(null); setStreetInput('');
    setSelectedArea(null); setAreaInput('');
    setStreetNumber('');

    setLoadingStreets(true);
    try {
      const r = await fetch('http://localhost:8080/api/address/streets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          state: selectedState,
          municipality: selectedMunicipality,
          postalCode: {
            label: postalCode,
            cosmoteCtx: null,
            vodafoneCtx: { label: postalCode, value: postalCode },
          },
        }),
      });
      const d = await r.json();
      setStreets(d.items || []);
    } catch (e) { console.error(e); }
    finally { setLoadingStreets(false); }
  };

  const handleStreetSelect = async (street) => {
    setSelectedStreet(street);
    setStreetInput(street.label);
    setSelectedArea(null); setAreaInput('');
    setStreetNumber('');
    setAreas([]);

    setLoadingAreas(true);
    try {
      const r = await fetch('http://localhost:8080/api/address/areas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(street),
      });
      const d = await r.json();
      const items = d.items || [];
      setAreas(items);
      if (items.length === 1) {
        setSelectedArea(items[0]);
        setAreaInput(items[0].label);
      }
    } catch (e) { console.error(e); }
    finally { setLoadingAreas(false); }
  };

  const handleAreaSelect = (area) => {
    setSelectedArea(area);
    setAreaInput(area.label);
  };

  const handleSearch = () => {
    if (!canSearch) return;
    onSearch({
      state: selectedState,
      municipality: selectedMunicipality,
      postalCode: {
        label: postalCode,
        cosmoteCtx: null,
        vodafoneCtx: { label: postalCode, value: postalCode },
      },
      street: selectedStreet,
      area: selectedArea,
      number: streetNumber,
      numberItem: null,
    });
  };

  // ── derived booleans ──────────────────────────────────────
  const canSearch =
    selectedState && selectedMunicipality && postalCode &&
    selectedStreet && streetNumber && !loading;

  return (
    <div className="search-wrap">
      <div className="search-card">
        <div className="form-horizontal">
          <div className="form-row-1">
            <DropdownField
              label="Prefecture"
              placeholder="Select…"
              value={stateInput}
              onChange={setStateInput}
              onSelect={handleStateSelect}
              items={states}
              open={open === 'state'}
              onOpen={v => setOpen(v ? 'state' : null)}
              disabled={loadingStates}
              loading={loadingStates}
              filled={!!selectedState}
            />
            <DropdownField
              label="Municipality"
              placeholder="Select…"
              value={municipalityInput}
              onChange={setMunicipalityInput}
              onSelect={handleMunicipalitySelect}
              items={municipalities}
              open={open === 'muni'}
              onOpen={v => setOpen(v ? 'muni' : null)}
              disabled={!selectedState || loadingMunis || municipalities.length === 0}
              loading={loadingMunis}
              filled={!!selectedMunicipality}
            />
            <TextField
              label="Postal Code"
              placeholder="e.g. 11141"
              value={postalCode}
              onChange={setPostalCode}
              onBlur={handlePostalBlur}
              disabled={!selectedMunicipality}
              filled={!!postalCode}
            />
          </div>

          <div className="form-row-2">
            <DropdownField
              label="Street"
              placeholder="Select…"
              value={streetInput}
              onChange={setStreetInput}
              onSelect={handleStreetSelect}
              items={streets}
              open={open === 'street'}
              onOpen={v => setOpen(v ? 'street' : null)}
              disabled={!postalCode || loadingStreets || streets.length === 0}
              loading={loadingStreets}
              filled={!!selectedStreet}
            />
            <DropdownField
              label="Area"
              placeholder="Select…"
              value={areaInput}
              onChange={setAreaInput}
              onSelect={handleAreaSelect}
              items={areas}
              open={open === 'area'}
              onOpen={v => setOpen(v ? 'area' : null)}
              disabled={!selectedStreet || loadingAreas || areas.length === 0}
              loading={loadingAreas}
              filled={!!selectedArea}
            />
            <TextField
              label="Number"
              placeholder="42"
              value={streetNumber}
              onChange={setStreetNumber}
              disabled={!selectedStreet}
              filled={!!streetNumber}
            />
            <button
              className="search-btn"
              onClick={handleSearch}
              disabled={!canSearch}
            >
              {loading ? '…' : 'Search'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}