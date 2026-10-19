/**
 * Morrow — Static Landing Page Script
 * Lightweight vanilla JavaScript for subtle dynamic enhancements.
 */
document.addEventListener('DOMContentLoaded', () => {
  // Update the copyright year automatically
  const yearElement = document.getElementById('current-year');
  if (yearElement) {
    const currentYear = new Date().getFullYear();
    yearElement.textContent = currentYear.toString();
  }

  // Ensure any external link has appropriate security attributes
  const externalLinks = document.querySelectorAll('a[target="_blank"]');
  externalLinks.forEach((link) => {
    const rel = link.getAttribute('rel') || '';
    if (!rel.includes('noopener')) {
      link.setAttribute('rel', `${rel} noopener noreferrer`.trim());
    }
  });
});
