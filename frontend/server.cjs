const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');

const app = express();
const PORT = 4173;

app.use(
  createProxyMiddleware({
    target: 'http://localhost:8080',
    changeOrigin: true,
    pathFilter:'/api',
  })
);

app.use(express.static(path.join(__dirname, 'dist')));

app.use((req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

app.listen(PORT, '0.0.0.0',() => {
  console.log(`Life Planner running at http://localhost:${PORT}`);
});