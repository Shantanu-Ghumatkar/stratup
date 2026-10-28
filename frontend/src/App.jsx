import { useState, useEffect } from 'react';
import './index.css';

const API_BASE = 'http://localhost:8081/api/pdf';

function App() {
  const getTodayDate = () => {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  };

  const [date, setDate] = useState(getTodayDate());
  const [startDate, setStartDate] = useState(getTodayDate());
  const [endDate, setEndDate] = useState(getTodayDate());
  const [images, setImages] = useState(null);
  const [sheets, setSheets] = useState([]);
  const [selectedSheet, setSelectedSheet] = useState('All');
  
  const [cities, setCities] = useState([]);
  const [selectedCity, setSelectedCity] = useState('');
  
  const [extractAds, setExtractAds] = useState(false);
  
  const [loading1, setLoading1] = useState(false);
  const [loading2, setLoading2] = useState(false);
  const [loading3, setLoading3] = useState(false);

  const [message, setMessage] = useState({ text: '', type: '' });

  useEffect(() => {
    fetch(`${API_BASE}/sheets`)
      .then(res => res.json())
      .then(data => {
        setSheets(data);
        if (data.length > 0 && selectedSheet !== 'All') {
          // Keep selected sheet if user changed it before load, else leave as All
        }
      })
      .catch(err => console.error('Failed to load sheets:', err));
  }, []);

  useEffect(() => {
    if (selectedSheet && selectedSheet !== 'All') {
      fetch(`${API_BASE}/cities?sheetName=${encodeURIComponent(selectedSheet)}`)
        .then(res => res.json())
        .then(data => {
          setCities(data);
          if (data.length > 0) setSelectedCity(data[0]);
          else setSelectedCity('');
        })
        .catch(err => console.error('Failed to load cities:', err));
    } else {
      setCities([]);
      setSelectedCity('');
    }
  }, [selectedSheet]);

  const showMessage = (text, type) => {
    setMessage({ text, type });
    setTimeout(() => setMessage({ text: '', type: '' }), 5000);
  };

  const handleDownload = async (url, filename, setLoading) => {
    setLoading(true);
    try {
      const response = await fetch(url);
      if (!response.ok) throw new Error('Network response was not ok');
      
      const blob = await response.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(downloadUrl);
      showMessage('Downloaded successfully!', 'success');
    } catch (error) {
      console.error('Error:', error);
      showMessage('Failed to generate PDF. Check backend console.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const createPdfByDate = () => {
    if (!date) return showMessage('Please select a date', 'error');
    const sheetParam = selectedSheet ? `&sheetName=${encodeURIComponent(selectedSheet)}` : '';
    const cityParam = selectedCity ? `&city=${encodeURIComponent(selectedCity)}` : '';
    const adsParam = extractAds ? `&extractAds=true` : '';
    const ext = selectedSheet === 'All' ? 'pdf' : 'pdf';
    handleDownload(`${API_BASE}/by-date?date=${date}${sheetParam}${cityParam}${adsParam}`, `News_${date}_${selectedCity || 'All'}.${ext}`, setLoading1);
  };

  const createPdfByDateRange = () => {
    if (!startDate || !endDate) return showMessage('Please select both start and end dates', 'error');
    const sheetParam = selectedSheet ? `&sheetName=${encodeURIComponent(selectedSheet)}` : '';
    const cityParam = selectedCity ? `&city=${encodeURIComponent(selectedCity)}` : '';
    const adsParam = extractAds ? `&extractAds=true` : '';
    handleDownload(`${API_BASE}/by-date-range?startDate=${startDate}&endDate=${endDate}${sheetParam}${cityParam}${adsParam}`, `News_${startDate}_to_${endDate}_${selectedCity || 'All'}.zip`, setLoading2);
  };

  const createPdfFromImages = async () => {
    if (!images || images.length === 0) return showMessage('Please select images', 'error');
    
    setLoading3(true);
    const formData = new FormData();
    for (let i = 0; i < images.length; i++) {
      formData.append('images', images[i]);
    }

    try {
      const response = await fetch(`${API_BASE}/from-images`, {
        method: 'POST',
        body: formData,
      });
      
      if (!response.ok) throw new Error('Network response was not ok');
      
      const blob = await response.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = 'Images.pdf';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(downloadUrl);
      showMessage('PDF generated successfully!', 'success');
    } catch (error) {
      console.error('Error:', error);
      showMessage('Failed to generate PDF from images.', 'error');
    } finally {
      setLoading3(false);
    }
  };

  return (
    <div className="app-container">
      <h1>News PDF Generator</h1>
      
      {message.text && (
        <div className={`message ${message.type}`}>
          {message.text}
        </div>
      )}

      <div className="section" style={{ padding: '1rem', marginBottom: '1.5rem', background: 'rgba(79, 70, 229, 0.1)', borderColor: 'var(--primary)' }}>
        <h2 style={{ marginBottom: '0.5rem', fontSize: '1rem' }}>Select Newspaper Source (Excel Sheet)</h2>
        <select 
          value={selectedSheet} 
          onChange={(e) => setSelectedSheet(e.target.value)}
          style={{ width: '100%', padding: '0.75rem', borderRadius: '8px', background: 'var(--bg-color)', color: 'white', border: '1px solid var(--primary)', outline: 'none', marginBottom: '1rem' }}
        >
          <option value="All">All Newspapers</option>
          {sheets.length === 0 && <option value="" disabled>(Backend Offline or Loading...)</option>}
          {sheets.map(sheet => (
            <option key={sheet} value={sheet}>{sheet}</option>
          ))}
        </select>

        <h2 style={{ marginBottom: '0.5rem', fontSize: '1rem' }}>Select City</h2>
        <select 
          value={selectedCity} 
          onChange={(e) => setSelectedCity(e.target.value)}
          style={{ width: '100%', padding: '0.75rem', borderRadius: '8px', background: 'var(--bg-color)', color: 'white', border: '1px solid var(--primary)', outline: 'none', marginBottom: '1rem' }}
        >
          <option value="">All Cities</option>
          {cities.map(city => (
            <option key={city} value={city}>{city}</option>
          ))}
        </select>

        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '1rem' }}>
          <input 
            type="checkbox" 
            checked={extractAds} 
            onChange={(e) => setExtractAds(e.target.checked)} 
            style={{ width: '20px', height: '20px', accentColor: 'var(--primary)' }}
          />
          Extract Ads (using AI/OCR)
        </label>
      </div>

      <div className="section">
        <h2>1. Create PDF by Date</h2>
        <div className="input-group">
          <input 
            type="date" 
            value={date} 
            onChange={(e) => setDate(e.target.value)} 
          />
          <button onClick={createPdfByDate} disabled={loading1}>
            {loading1 ? <div className="loader"></div> : 'Generate PDF'}
          </button>
        </div>
      </div>

      <div className="section">
        <h2>2. Create PDFs by Date Range</h2>
        <div className="input-group">
          <input 
            type="date" 
            placeholder="Start Date"
            value={startDate} 
            onChange={(e) => setStartDate(e.target.value)} 
          />
          <input 
            type="date" 
            placeholder="End Date"
            value={endDate} 
            onChange={(e) => setEndDate(e.target.value)} 
          />
          <button onClick={createPdfByDateRange} disabled={loading2}>
            {loading2 ? <div className="loader"></div> : 'Generate ZIP'}
          </button>
        </div>
      </div>

      <div className="section">
        <h2>3. Create PDF from Images</h2>
        <div className="input-group">
          <input 
            type="file" 
            multiple 
            accept="image/*"
            onChange={(e) => setImages(e.target.files)} 
          />
          <button onClick={createPdfFromImages} disabled={loading3}>
            {loading3 ? <div className="loader"></div> : 'Upload & Generate'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default App;
