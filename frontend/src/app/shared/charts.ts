import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';

// Register only what the app uses so the bundle stays small.
Chart.register(
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Filler,
);

Chart.defaults.font.family = "'IBM Plex Sans', system-ui, sans-serif";
Chart.defaults.font.size = 13;
// Matches --muted, which keeps axis labels at 4.5:1 or more on white.
Chart.defaults.color = '#4d5d58';

/** Keep in sync with the colour tokens in styles.css. */
export const chartColors = {
  price: '#2f6f8f',
  priceMuted: '#5f93aa',
  accent: '#0f6b5c',
  ink: '#14231f',
  co2: '#8a5a14',
  grid: '#e3e9e7',
};

/** Fewer x-axis labels on narrow screens so they never overlap. */
export const tickLimit = (width: number): number => (width < 420 ? 5 : width < 700 ? 8 : 12);

export { Chart };
