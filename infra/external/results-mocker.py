import json

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime, timedelta


HOST = "0.0.0.0"
PORT = 8085

WORLD_CUP_TOURNAMENT_ID = "107"
CURRENT_TIME = datetime.now()

MATCHES = [
    {"home": "MEX", "away": "RSA", "home_goals": 0, "away_goals": 0, "home_odds": 1.45, "tie_odds": 4.07, "away_odds": 5.75, "status": "TO_START", "starts_at": datetime.strptime("2026-06-11 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "KOR", "away": "CZE", "home_goals": 0, "away_goals": 0, "home_odds": 2.57, "tie_odds": 3.24, "away_odds": 2.57, "status": "TO_START", "starts_at": datetime.strptime("2026-06-12 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CAN", "away": "BIH", "home_goals": 0, "away_goals": 0, "home_odds": 2.00, "tie_odds": 3.63, "away_odds": 3.09, "status": "TO_START", "starts_at": datetime.strptime("2026-06-12 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "USA", "away": "PAR", "home_goals": 0, "away_goals": 0, "home_odds": 1.86, "tie_odds": 3.47, "away_odds": 3.80, "status": "TO_START", "starts_at": datetime.strptime("2026-06-13 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "BRA", "away": "MAR", "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.89, "away_odds": 5.50, "status": "TO_START", "starts_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "QAT", "away": "SUI", "home_goals": 0, "away_goals": 0, "home_odds": 7.94, "tie_odds": 5.01, "away_odds": 1.29, "status": "TO_START", "starts_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "HAI", "away": "SCO", "home_goals": 0, "away_goals": 0, "home_odds": 6.31, "tie_odds": 4.79, "away_odds": 1.38, "status": "TO_START", "starts_at": datetime.strptime("2026-06-14 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "AUS", "away": "TUR", "home_goals": 0, "away_goals": 0, "home_odds": 4.47, "tie_odds": 3.72, "away_odds": 1.66, "status": "TO_START", "starts_at": datetime.strptime("2026-06-14 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "GER", "away": "CUW", "home_goals": 0, "away_goals": 0, "home_odds": 1.02, "tie_odds": 21.0, "away_odds": 51.0, "status": "TO_START", "starts_at": datetime.strptime("2026-06-14 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NED", "away": "JPN", "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.63, "away_odds": 3.47, "status": "TO_START", "starts_at": datetime.strptime("2026-06-14 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CIV", "away": "ECU", "home_goals": 0, "away_goals": 0, "home_odds": 3.09, "tie_odds": 3.24, "away_odds": 2.14, "status": "TO_START", "starts_at": datetime.strptime("2026-06-14 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SWE", "away": "TUN", "home_goals": 0, "away_goals": 0, "home_odds": 1.82, "tie_odds": 3.47, "away_odds": 3.80, "status": "TO_START", "starts_at": datetime.strptime("2026-06-15 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ESP", "away": "CPV", "home_goals": 0, "away_goals": 0, "home_odds": 1.05, "tie_odds": 17.0, "away_odds": 34.0, "status": "TO_START", "starts_at": datetime.strptime("2026-06-15 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "BEL", "away": "EGY", "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.98, "away_odds": 5.50, "status": "TO_START", "starts_at": datetime.strptime("2026-06-15 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "KSA", "away": "URU", "home_goals": 0, "away_goals": 0, "home_odds": 5.01, "tie_odds": 3.80, "away_odds": 1.58, "status": "TO_START", "starts_at": datetime.strptime("2026-06-15 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "IRN", "away": "NZL", "home_goals": 0, "away_goals": 0, "home_odds": 1.66, "tie_odds": 3.63, "away_odds": 4.47, "status": "TO_START", "starts_at": datetime.strptime("2026-06-16 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "FRA", "away": "SEN", "home_goals": 0, "away_goals": 0, "home_odds": 1.41, "tie_odds": 4.47, "away_odds": 6.31, "status": "TO_START", "starts_at": datetime.strptime("2026-06-16 09:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "IRQ", "away": "NOR", "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 5.01, "away_odds": 1.29, "status": "TO_START", "starts_at": datetime.strptime("2026-06-16 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ARG", "away": "ALG", "home_goals": 0, "away_goals": 0, "home_odds": 1.38, "tie_odds": 4.47, "away_odds": 7.08, "status": "TO_START", "starts_at": datetime.strptime("2026-06-17 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "AUT", "away": "JOR", "home_goals": 0, "away_goals": 0, "home_odds": 1.32, "tie_odds": 4.79, "away_odds": 8.51, "status": "TO_START", "starts_at": datetime.strptime("2026-06-17 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "POR", "away": "COD", "home_goals": 0, "away_goals": 0, "home_odds": 1.29, "tie_odds": 4.79, "away_odds": 8.91, "status": "TO_START", "starts_at": datetime.strptime("2026-06-17 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ENG", "away": "CRO", "home_goals": 0, "away_goals": 0, "home_odds": 1.62, "tie_odds": 3.89, "away_odds": 4.47, "status": "TO_START", "starts_at": datetime.strptime("2026-06-17 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "GHA", "away": "PAN", "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.39, "away_odds": 3.47, "status": "TO_START", "starts_at": datetime.strptime("2026-06-17 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "UZB", "away": "COL", "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 4.47, "away_odds": 1.35, "status": "TO_START", "starts_at": datetime.strptime("2026-06-18 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CZE", "away": "RSA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-18 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SUI", "away": "BIH", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-18 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CAN", "away": "QAT", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-18 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "MEX", "away": "KOR", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-19 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "USA", "away": "AUS", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-19 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SCO", "away": "MAR", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-19 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "BRA", "away": "HAI", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-20 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "TUR", "away": "PAR", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-20 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NED", "away": "SWE", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-20 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "GER", "away": "CIV", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-20 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ECU", "away": "CUW", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-21 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "TUN", "away": "JPN", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-21 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ESP", "away": "KSA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-21 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "BEL", "away": "IRN", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-21 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "URU", "away": "CPV", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-21 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NZL", "away": "EGY", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-22 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ARG", "away": "AUT", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-22 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "FRA", "away": "IRQ", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-22 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NOR", "away": "SEN", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-23 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "JOR", "away": "ALG", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-23 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "POR", "away": "UZB", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-23 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ENG", "away": "GHA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-23 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "PAN", "away": "CRO", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-23 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "COL", "away": "COD", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-24 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "BIH", "away": "QAT", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SUI", "away": "CAN", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "MAR", "away": "HAI", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SCO", "away": "BRA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "RSA", "away": "KOR", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CZE", "away": "MEX", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CUW", "away": "CIV", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ECU", "away": "GER", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "JPN", "away": "SWE", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "TUN", "away": "NED", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "TUR", "away": "USA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "PAR", "away": "AUS", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "SEN", "away": "IRQ", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NOR", "away": "FRA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CPV", "away": "KSA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "URU", "away": "ESP", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "EGY", "away": "IRN", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "NZL", "away": "BEL", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "PAN", "away": "ENG", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "CRO", "away": "GHA", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "COL", "away": "POR", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "COD", "away": "UZB", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "JOR", "away": "ARG", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"home": "ALG", "away": "AUT", "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "status": "TO_START", "starts_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S")},
]

def adjust_dates():
    # target: first date should be 15 minutes in the future
    target_start = datetime.now() + timedelta(minutes=15)

    # calculate shift delta
    delta = target_start - MATCHES[0]['starts_at']

    # apply same shift to all dates
    for match in MATCHES:
        match['starts_at'] += delta

class ResultsMockerHandler(BaseHTTPRequestHandler):

    def _process_matches_request(self):
        parsed_url = urlparse(self.path)
        params = parse_qs(parsed_url.query)

        tournament_id = params.get("tournament_id", [None])[0]
        if tournament_id != WORLD_CUP_TOURNAMENT_ID:
            return 404, {"error": "Tournament not found"}
        return 200, MATCHES

    def _send_json(self, code, payload):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(payload, default=str).encode())

    def do_GET(self):
        if urlparse(self.path).path == "/api-client/matches/live.json":
            status_code, response = self._process_matches_request()
            self._send_json(status_code, response)
        else:
            self._send_json(405, {"error": "Method not allowed"})

    # reject everything else
    def do_POST(self):
        self._reject()

    def do_PUT(self):
        self._reject()

    def do_PATCH(self):
        self._reject()

    def do_DELETE(self):
        self._reject()

    def _reject(self):
        self._send_json(405, {"error": "Method not allowed"})


if __name__ == "__main__":
    print("Adjusting dates for matches")
    adjust_dates()

    server = HTTPServer((HOST, PORT), ResultsMockerHandler)
    print(f"Server running on http://{HOST}:{PORT}")
    server.serve_forever()
