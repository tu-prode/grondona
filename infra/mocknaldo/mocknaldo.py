import json
import math
import random
import threading
import time

import sys
import logging

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(logging.Formatter(
    "%(asctime)s - %(levelname)s - %(message)s"
))

logger.handlers.clear()
logger.addHandler(handler)

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import date, datetime, timedelta

stop = False
active = True

WORLD_CUP_COMPETITION_ID = "2173492"
HOST = "0.0.0.0"
PORT = 8085

TIME_LOCK = threading.Lock()
current = datetime.now()
checkpoint = None
delta = 15 * 60

import signal
def handle_shutdown(signum):
    global stop
    print(f"Received signal {signum}, shutting down gracefully...")
    stop = True

# Handle Docker stop signals
signal.signal(signal.SIGTERM, handle_shutdown)
signal.signal(signal.SIGINT, handle_shutdown)

MATCHES_LOCK = threading.Lock()
MATCHES = [
    {"code": "1", "home": "MEX", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.45, "tie_odds": 4.07, "away_odds": 5.75, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-11 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "2", "home": "KOR", "away": "CZE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 2.57, "tie_odds": 3.24, "away_odds": 2.57, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-12 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "3", "home": "CAN", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 2.00, "tie_odds": 3.63, "away_odds": 3.09, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-12 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "4", "home": "USA", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.86, "tie_odds": 3.47, "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "7", "home": "BRA", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.89, "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "8", "home": "QAT", "away": "SUI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.94, "tie_odds": 5.01, "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "5", "home": "HAI", "away": "SCO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 6.31, "tie_odds": 4.79, "away_odds": 1.38, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "6", "home": "AUS", "away": "TUR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 4.47, "tie_odds": 3.72, "away_odds": 1.66, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "9", "home": "GER", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.02, "tie_odds": 21.0, "away_odds": 51.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "11", "home": "NED", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.63, "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "10", "home": "CIV", "away": "ECU", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 3.09, "tie_odds": 3.24, "away_odds": 2.14, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "12", "home": "SWE", "away": "TUN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.82, "tie_odds": 3.47, "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "14", "home": "ESP", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.05, "tie_odds": 17.0, "away_odds": 34.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "16", "home": "BEL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.98, "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "13", "home": "KSA", "away": "URU", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 5.01, "tie_odds": 3.80, "away_odds": 1.58, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "15", "home": "IRN", "away": "NZL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.66, "tie_odds": 3.63, "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "17", "home": "FRA", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.41, "tie_odds": 4.47, "away_odds": 6.31, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 09:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "18", "home": "IRQ", "away": "NOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 5.01, "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "19", "home": "ARG", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.38, "tie_odds": 4.47, "away_odds": 7.08, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "20", "home": "AUT", "away": "JOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.32, "tie_odds": 4.79, "away_odds": 8.51, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "23", "home": "POR", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.29, "tie_odds": 4.79, "away_odds": 8.91, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "22", "home": "ENG", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.62, "tie_odds": 3.89, "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "21", "home": "GHA", "away": "PAN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.39, "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "24", "home": "UZB", "away": "COL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 4.47, "away_odds": 1.35, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "25", "home": "CZE", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "26", "home": "SUI", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "27", "home": "CAN", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "28", "home": "MEX", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "32", "home": "USA", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "30", "home": "SCO", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "29", "home": "BRA", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "31", "home": "TUR", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "35", "home": "NED", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "33", "home": "GER", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "34", "home": "ECU", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "36", "home": "TUN", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "38", "home": "ESP", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "39", "home": "BEL", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "37", "home": "URU", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "40", "home": "NZL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "43", "home": "ARG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "42", "home": "FRA", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "41", "home": "NOR", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "44", "home": "JOR", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "47", "home": "POR", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "45", "home": "ENG", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "46", "home": "PAN", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "48", "home": "COL", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "52", "home": "BIH", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "51", "home": "SUI", "away": "CAN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "50", "home": "MAR", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "49", "home": "SCO", "away": "BRA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "54", "home": "RSA", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "53", "home": "CZE", "away": "MEX", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "55", "home": "CUW", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "56", "home": "ECU", "away": "GER", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "57", "home": "JPN", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "58", "home": "TUN", "away": "NED", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "59", "home": "TUR", "away": "USA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "60", "home": "PAR", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "62", "home": "SEN", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "61", "home": "NOR", "away": "FRA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "65", "home": "CPV", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "66", "home": "URU", "away": "ESP", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "63", "home": "EGY", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "64", "home": "NZL", "away": "BEL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "67", "home": "PAN", "away": "ENG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "68", "home": "CRO", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "71", "home": "COL", "away": "POR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "72", "home": "COD", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "69", "home": "JOR", "away": "ARG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "70", "home": "ALG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
]

def odds_to_probabilities(odd_a, odd_draw, odd_b):
    inv_a = 1 / odd_a
    inv_d = 1 / odd_draw
    inv_b = 1 / odd_b
    total = inv_a + inv_d + inv_b
    return inv_a / total, inv_d / total, inv_b / total

def poisson(lmbda):
    L = math.exp(-lmbda)
    k = 0
    p = 1
    while p > L:
        k += 1
        p *= random.random()
    return k - 1

def simulate_score(minute, match):
    # calculating match probs
    inv_a = 1 / match["home_odds"]
    inv_d = 1 / match["tie_odds"]
    inv_b = 1 / match["away_odds"]
    total = inv_a + inv_d + inv_b
    prob_home, prob_draw, prob_away = inv_a / total, inv_d / total, inv_b / total

    # setting total expected goals
    progress = minute / 90
    average_goals = 2.7
    imbalance = abs(prob_home - prob_away)
    goal_inflation = 1 + 0.5 * imbalance # for huge imbalance, more goals
    expected_total_goals = average_goals * goal_inflation * progress

    # set each team goal share
    strength_a = prob_home + 0.5 * prob_draw
    strength_b = prob_away + 0.5 * prob_draw
    share_a = strength_a / (strength_a + strength_b)
    share_b = strength_b / (strength_a + strength_b)
    lambda_a = expected_total_goals * share_a
    lambda_b = expected_total_goals * share_b

    # calculating goals
    goals_a = poisson(lambda_a)
    goals_b = poisson(lambda_b)
    return int(goals_a), int(goals_b)


def simulate_added(half):
    if half == 1:
        mean = 2.5
        std = 1.0
    else:
        mean = 4.5
        std = 1.8

    value = random.gauss(mean, std)
    value = max(0, min(value, 12))
    return int(round(value))


def diff_minutes(date1, date2):
    return int((date1 - date2).total_seconds() / 60)

def diff_hours(date1, date2):
    return int(diff_minutes(date1, date2) / 60)

def or_zero(number):
    return number if number is not None else 0


def simulate_matches():
    while not stop:
        time.sleep(1)
        if active:

            global checkpoint, current, delta
            TIME_LOCK.acquire()
            current = current + timedelta(seconds=delta)
            TIME_LOCK.release()
            if not checkpoint or current.date() != checkpoint:
                logger.debug(f"Simulating @ {current.date()}")
                checkpoint = current.date()

            for match in MATCHES:
                if match["status"] == "COMPLETED":
                    continue

                match_ended = False
                if current > match["started_at"]:
                    new_status = "IN_PLAY"
                    minutes = diff_minutes(current, match["started_at"])
                    logger.debug(f'Updating match: {match["home"]}-{match["away"]}')
                    logger.debug(f'Minutes from start timestamp: {minutes}\'')

                    new_half, new_minutes, new_ended_at = None, None, None
                    new_added_time1, new_added_time2 = match["first_half_added_time"], match["second_half_added_time"]
                    if minutes < 45:
                        new_half = 1
                        new_minutes = minutes
                    else:
                        if not new_added_time1:
                            new_added_time1 = simulate_added(half=1)
                            logger.debug(f'Added time for first half: {new_added_time1}\'')
                        if minutes < 50 + new_added_time1:
                            new_half = 1
                            new_minutes = 45 + new_added_time1
                        elif minutes < 65 + new_added_time1:
                            new_half = 1
                            new_minutes = 45 + new_added_time1
                            new_status = "HALF_TIME"
                        elif minutes < 110 + new_added_time1:
                            new_half = 2
                            new_minutes = minutes - (new_added_time1 + 20)
                        else:
                            new_half = 2
                            new_added_time2 = simulate_added(half=2)
                            logger.debug(f'Added time for second half: {new_added_time1}\'')
                            new_minutes = 90 + new_added_time2
                            match_ended = True
                            new_status = "COMPLETED"

                    home_goals, away_goals = simulate_score(new_minutes + or_zero(new_added_time1) + or_zero(new_added_time2), match)
                    new_home_goals = max(match["home_goals"], home_goals)
                    new_away_goals = max(match["away_goals"], away_goals)

                    logger.debug(f'Updated match: ({new_status}) {match["home"]} {new_home_goals}-{new_away_goals} {match["away"]}, {new_minutes}\' ({new_half}H)')
                    if match_ended:
                        logger.info(f'Match ended: {match["home"]} {new_home_goals}-{new_away_goals} {match["away"]}')
                        new_ended_at = match["started_at"] + timedelta(minutes=(115 + new_added_time1 + new_added_time2))

                    MATCHES_LOCK.acquire()
                    match["status"] = new_status
                    match["home_goals"] = int(new_home_goals)
                    match["away_goals"] = int(new_away_goals)
                    match["half"] = int(new_half)
                    match["minutes"] = int(new_minutes)
                    match["first_half_added_time"] = new_added_time1
                    match["second_half_added_time"] = new_added_time2
                    match["ended_at"] = new_ended_at
                    MATCHES_LOCK.release()

                    if match["started_at"] > current:
                        break

                else:
                    # Not yet played
                    if not match["odds_calculated_at"] or diff_hours(current, match["odds_calculated_at"]) >= 24:
                        match["home_odds"] = round(match["home_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["tie_odds"] = round(match["tie_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["away_odds"] = round(match["away_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["odds_calculated_at"] = current


def json_serializer(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")


class ResultsMockerHandler(BaseHTTPRequestHandler):

    @staticmethod
    def _accept_pause():
        global active
        if active:
            logger.info("Pause received")
        else:
            logger.info("Resume received")
        active = not active
        return 202

    @staticmethod
    def _accept_stop():
        global stop
        logger.info("Stop received")
        stop = not stop
        return 202


    def _accept_current(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_time = body.get("time")
        if not new_time:
            return 400, {"error": "'time' is required"}
        try:
            global current
            current = datetime.fromisoformat(new_time)
            logger.info(f"Current changed to {new_time}")
            for match in MATCHES:
                match["odds_changed_at"] = current
            return 202, None
        except ValueError:
            return 400, {"error": "Invalid datetime format"}


    def _accept_delta(self):
        global delta
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_delta_mins = body.get("mins")
        if new_delta_mins is not None:
            try:
                delta = int(new_delta_mins * 60)
                logger.info(f"Delta changed to {new_delta_mins} minutes")
                return 200, None
            except ValueError:
                return 400, {"error": "Invalid number format"}
        new_delta_secs = body.get("secs")
        if new_delta_secs is not None:
            try:
                delta = int(new_delta_secs)
                logger.info(f"Delta changed to {new_delta_mins} seconds")
                return 200, None
            except ValueError:
                return 400, {"error": "Invalid number format"}
        return 400, {"error": "Missing delta"}


    def _process_matches_request(self):
        parsed_url = urlparse(self.path)
        params = parse_qs(parsed_url.query)

        competition_id = params.get("competition_id", [None])[0]
        if competition_id != WORLD_CUP_COMPETITION_ID:
            return 404, {"error": f"Tournament {competition_id} not found"}
        return 200, None


    def _process_new_matches(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_matches = body.get("matches")
        if not new_matches:
            return 400, {"error": "'matches' is required"}
        try:
            global MATCHES
            MATCHES_LOCK.acquire()
            MATCHES += new_matches
            MATCHES_LOCK.release()
            logger.info(f"New matches added: {len(new_matches)}")
            return 201, None
        except ValueError:
            return 400, {"error": "Invalid number format"}


    def _send_matches(self, code, payload=None):
        if payload is not None:
            self._send_json(code, payload)
            return
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        TIME_LOCK.acquire()
        MATCHES_LOCK.acquire()
        self.wfile.write(json.dumps({"current": current, "matches": MATCHES}, default=json_serializer).encode())
        MATCHES_LOCK.release()
        TIME_LOCK.release()

    def _send_json(self, code, payload=None):
        self.send_response(code)
        if payload is not None:
            self.send_header("Content-Type", "application/json")
        self.end_headers()
        if payload is not None:
            self.wfile.write(json.dumps(payload, default=str).encode())

    def do_GET(self):
        if urlparse(self.path).path == "/api-client/matches/live.json":
            status_code, response = self._process_matches_request()
            self._send_matches(status_code, response)
        else:
            self._reject()

    def do_PUT(self):
        if urlparse(self.path).path == "/pause":
            status_code= self._accept_pause()
            self._send_json(status_code)
        elif urlparse(self.path).path == "/update-time":
            status_code, response = self._accept_current()
            self._send_json(status_code, response)
        elif urlparse(self.path).path == "/update-delta":
            status_code, response = self._accept_delta()
            self._send_json(status_code, response)
        elif urlparse(self.path).path == "/stop":
            status_code = self._accept_stop()
            self._send_json(status_code)
        else:
            self._reject()

    def do_POST(self):
        if urlparse(self.path).path == "/new-matches":
            status_code = self._accept_stop()
            self._send_json(status_code)
        else:
            self._reject()

    # reject everything else
    def do_PATCH(self):
        self._reject()

    def do_DELETE(self):
        self._reject()

    def _reject(self):
        self._send_json(405, {"error": "Method not allowed"})


if __name__ == "__main__":
    logger.info("Starting daemon")
    thread = threading.Thread(target=simulate_matches, daemon=True)
    thread.start()

    server = HTTPServer((HOST, PORT), ResultsMockerHandler)
    logger.info(f"Server running on http://{HOST}:{PORT}")
    server.serve_forever()
