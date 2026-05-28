import json
import math
import random
import threading
import time

import sys
import logging

from copy import deepcopy
from zoneinfo import ZoneInfo

DEFAULT_TZ = ZoneInfo("America/Argentina/Buenos_Aires")

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
COMBINATIONS_CSV = "./combinations.csv"

HOST = "0.0.0.0"
PORT = 8085

TIME_LOCK = threading.Lock()
current = datetime.now()
checkpoint = None
delta = 51

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
    {"code": "1", "group": "A", "home": "MEX", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.45, "draw_odds": 4.07,
     "away_odds": 5.75, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-11 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "2", "group": "A", "home": "KOR", "away": "CZE", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.57, "draw_odds": 3.24,
     "away_odds": 2.57, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-12 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "3", "group": "B", "home": "CAN", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.00, "draw_odds": 3.63,
     "away_odds": 3.09, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-12 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "4", "group": "D", "home": "USA", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.86, "draw_odds": 3.47,
     "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-13 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "7", "group": "C", "home": "BRA", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.51, "draw_odds": 3.89,
     "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "8", "group": "B", "home": "QAT", "away": "SUI", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 7.94, "draw_odds": 5.01,
     "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "5", "group": "C", "home": "HAI", "away": "SCO", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 6.31, "draw_odds": 4.79,
     "away_odds": 1.38, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-14 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "6", "group": "D", "home": "AUS", "away": "TUR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 4.47, "draw_odds": 3.72,
     "away_odds": 1.66, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-14 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "9", "group": "E", "home": "GER", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.02, "draw_odds": 21.0,
     "away_odds": 51.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-14 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "11", "group": "F", "home": "NED", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.91, "draw_odds": 3.63,
     "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-14 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "10", "group": "E", "home": "CIV", "away": "ECU", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.09, "draw_odds": 3.24,
     "away_odds": 2.14, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-14 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "12", "group": "F", "home": "SWE", "away": "TUN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.82, "draw_odds": 3.47,
     "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-15 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "14", "group": "H", "home": "ESP", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.05, "draw_odds": 17.0,
     "away_odds": 34.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-15 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "16", "group": "G", "home": "BEL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.51, "draw_odds": 3.98,
     "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-15 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "13", "group": "H", "home": "KSA", "away": "URU", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 5.01, "draw_odds": 3.80,
     "away_odds": 1.58, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-15 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "15", "group": "G", "home": "IRN", "away": "NZL", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.66, "draw_odds": 3.63,
     "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-16 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "17", "group": "I", "home": "FRA", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.41, "draw_odds": 4.47,
     "away_odds": 6.31, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-16 09:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "18", "group": "I", "home": "IRQ", "away": "NOR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 7.08, "draw_odds": 5.01,
     "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-16 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "19", "group": "J", "home": "ARG", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.38, "draw_odds": 4.47,
     "away_odds": 7.08, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-17 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "20", "group": "J", "home": "AUT", "away": "JOR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.32, "draw_odds": 4.79,
     "away_odds": 8.51, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-17 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "23", "group": "K", "home": "POR", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.29, "draw_odds": 4.79,
     "away_odds": 8.91, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-17 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "22", "group": "L", "home": "ENG", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.62, "draw_odds": 3.89,
     "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-17 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "21", "group": "L", "home": "GHA", "away": "PAN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.91, "draw_odds": 3.39,
     "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-17 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "24", "group": "K", "home": "UZB", "away": "COL", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 7.08, "draw_odds": 4.47,
     "away_odds": 1.35, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-18 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "25", "group": "A", "home": "CZE", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.72, "draw_odds": 3.50,
     "away_odds": 5.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-18 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "26", "group": "B", "home": "SUI", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.62, "draw_odds": 3.60,
     "away_odds": 5.66, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-18 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "27", "group": "B", "home": "CAN", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.55, "draw_odds": 3.70,
     "away_odds": 6.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-18 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "28", "group": "A", "home": "MEX", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.87, "draw_odds": 3.33,
     "away_odds": 4.20, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-19 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "32", "group": "D", "home": "USA", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.77, "draw_odds": 4.00,
     "away_odds": 6.07, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-19 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "30", "group": "C", "home": "SCO", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.85, "draw_odds": 3.15,
     "away_odds": 2.05, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-19 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "29", "group": "C", "home": "BRA", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.05, "draw_odds": 11.0,
     "away_odds": 53.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-20 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "31", "group": "D", "home": "TUR", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.12, "draw_odds": 3.20,
     "away_odds": 3.55, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-20 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "35", "group": "F", "home": "NED", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.72, "draw_odds": 3.75,
     "away_odds": 4.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-20 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "33", "group": "E", "home": "GER", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.53, "draw_odds": 4.00,
     "away_odds": 6.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-20 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "34", "group": "E", "home": "ECU", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.24, "draw_odds": 5.50,
     "away_odds": 13.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-21 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "36", "group": "F", "home": "TUN", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 4.75, "draw_odds": 3.30,
     "away_odds": 1.81, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-21 04:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "38", "group": "H", "home": "ESP", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.14, "draw_odds": 7.00,
     "away_odds": 22.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-21 16:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "39", "group": "G", "home": "BEL", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.57, "draw_odds": 3.14,
     "away_odds": 5.11, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-21 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "37", "group": "H", "home": "URU", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.44, "draw_odds": 4.30,
     "away_odds": 7.25, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-21 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "40", "group": "G", "home": "NZL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.68, "draw_odds": 3.65,
     "away_odds": 1.68, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-22 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "43", "group": "J", "home": "ARG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.53, "draw_odds": 4.00,
     "away_odds": 6.25, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-22 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "42", "group": "I", "home": "FRA", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.13, "draw_odds": 6.75,
     "away_odds": 26.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-22 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "41", "group": "I", "home": "NOR", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.05, "draw_odds": 3.30,
     "away_odds": 3.66, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-23 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "44", "group": "J", "home": "JOR", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 7.00, "draw_odds": 3.66,
     "away_odds": 1.53, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-23 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "47", "group": "K", "home": "POR", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.17, "draw_odds": 6.50,
     "away_odds": 17.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-23 17:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "45", "group": "L", "home": "ENG", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.33, "draw_odds": 4.75,
     "away_odds": 9.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-23 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "46", "group": "L", "home": "PAN", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 8.50, "draw_odds": 4.40,
     "away_odds": 1.38, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-23 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "48", "group": "K", "home": "COL", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.57, "draw_odds": 4.05,
     "away_odds": 6.95, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-24 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "52", "group": "B", "home": "BIH", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.85, "draw_odds": 3.40,
     "away_odds": 4.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "51", "group": "B", "home": "SUI", "away": "CAN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.95, "draw_odds": 3.30,
     "away_odds": 3.90, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "50", "group": "C", "home": "MAR", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.30, "draw_odds": 5.00,
     "away_odds": 9.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "49", "group": "C", "home": "SCO", "away": "BRA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 6.50, "draw_odds": 4.20,
     "away_odds": 1.45, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "54", "group": "A", "home": "RSA", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.10, "draw_odds": 3.20,
     "away_odds": 2.30, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "53", "group": "A", "home": "CZE", "away": "MEX", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.00, "draw_odds": 3.25,
     "away_odds": 2.40, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "55", "group": "E", "home": "CUW", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 5.50, "draw_odds": 3.80,
     "away_odds": 1.60, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "56", "group": "E", "home": "ECU", "away": "GER", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 4.20, "draw_odds": 3.60,
     "away_odds": 1.75, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "57", "group": "F", "home": "JPN", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.50, "draw_odds": 3.10,
     "away_odds": 2.70, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "58", "group": "F", "home": "TUN", "away": "NED", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 5.80, "draw_odds": 3.90,
     "away_odds": 1.55, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "59", "group": "D", "home": "TUR", "away": "USA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.60, "draw_odds": 3.20,
     "away_odds": 2.60, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "60", "group": "D", "home": "PAR", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.10, "draw_odds": 3.25,
     "away_odds": 3.40, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "62", "group": "I", "home": "SEN", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.60, "draw_odds": 3.70,
     "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "61", "group": "I", "home": "NOR", "away": "FRA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 5.00, "draw_odds": 3.80,
     "away_odds": 1.75, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "65", "group": "H", "home": "CPV", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.70, "draw_odds": 3.10,
     "away_odds": 2.60, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "66", "group": "H", "home": "URU", "away": "ESP", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.10, "draw_odds": 3.20,
     "away_odds": 2.20, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "63", "group": "G", "home": "EGY", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 2.30, "draw_odds": 3.00,
     "away_odds": 3.20, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "64", "group": "G", "home": "NZL", "away": "BEL", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 12.0, "draw_odds": 6.00,
     "away_odds": 1.20, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "67", "group": "L", "home": "PAN", "away": "ENG", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 9.00, "draw_odds": 4.80,
     "away_odds": 1.30, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "68", "group": "L", "home": "CRO", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.85, "draw_odds": 3.30,
     "away_odds": 4.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "71", "group": "K", "home": "COL", "away": "POR", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.20, "draw_odds": 3.25,
     "away_odds": 2.15, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "72", "group": "K", "home": "COD", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.00, "draw_odds": 3.10,
     "away_odds": 2.40, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "69", "group": "J", "home": "JOR", "away": "ARG", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 15.0, "draw_odds": 6.50,
     "away_odds": 1.15, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
    {"code": "70", "group": "J", "home": "ALG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None,
     "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
     "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
     "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 3.10, "draw_odds": 3.20,
     "away_odds": 2.25, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
     "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S"), "ended_at": None},
]

#######################################################################################################################
#                                           GENERATE MATCHES FROM RESULTS                                             #
#######################################################################################################################

groups_flag = False


def check_round(match):
    global groups_flag, MATCHES
    code = match["code"]
    if match["code"] in ["69", "70", "87", "96", "100", "102"]:
        if match["code"] in ["69", "70"] and not groups_flag:
            groups_flag = True
            return
        new_matches = []
        if int(code) <= 72:
            # Generate RO32
            standings = calculate_group_standings()
            combination = calculate_ro32_combination(standings)
            ro32_1 = new_match("73", standings["A"][1]["team"], standings["B"][1]["team"], "2026-06-28 16:00:00")
            ro32_2 = new_match("76", standings["C"][0]["team"], standings["F"][1]["team"], "2026-06-29 14:00:00")
            ro32_3 = new_match("74", standings["E"][0]["team"], standings[combination[4]][2]["team"], "2026-06-29 17:30:00")
            ro32_4 = new_match("75", standings["F"][0]["team"], standings["C"][1]["team"], "2026-06-29 22:00:00")
            ro32_5 = new_match("78", standings["E"][1]["team"], standings["I"][1]["team"], "2026-06-30 14:00:00")
            ro32_6 = new_match("77", standings["I"][0]["team"], standings[combination[6]][2]["team"], "2026-06-30 18:00:00")
            ro32_7 = new_match("79", standings["A"][0]["team"], standings[combination[1]][2]["team"], "2026-06-30 22:00:00")
            ro32_8 = new_match("80", standings["L"][0]["team"], standings[combination[8]][2]["team"], "2026-07-01 13:00:00")
            ro32_9 = new_match("82", standings["G"][0]["team"], standings[combination[5]][2]["team"], "2026-07-01 17:00:00")
            ro32_10 = new_match("81", standings["D"][0]["team"], standings[combination[3]][2]["team"], "2026-07-01 21:00:00")
            ro32_11 = new_match("84", standings["H"][0]["team"], standings["J"][1]["team"], "2026-07-02 16:00:00")
            ro32_12 = new_match("83", standings["K"][1]["team"], standings["L"][1]["team"], "2026-07-02 20:00:00")
            ro32_13 = new_match("85", standings["B"][0]["team"], standings[combination[2]][2]["team"], "2026-07-03 00:00:00")
            ro32_14 = new_match("88", standings["D"][1]["team"], standings["G"][1]["team"], "2026-07-03 15:00:00")
            ro32_15 = new_match("86", standings["J"][0]["team"], standings["H"][1]["team"], "2026-07-03 19:00:00")
            ro32_16 = new_match("87", standings["K"][0]["team"], standings[combination[7]][2]["team"], "2026-07-03 22:30:00")
            new_matches += [ ro32_1, ro32_2, ro32_3, ro32_4, ro32_5, ro32_6, ro32_7, ro32_8, ro32_9,
                             ro32_10, ro32_11, ro32_12, ro32_13, ro32_14, ro32_15, ro32_16]
            for match in new_matches:
                logger.info(f"New match in Ro32: {match['home']}-{match['away']}")
        elif int(code) <= 87:
            # Generate RO16
            ro16_1 = next_knockout_match("89", "74", "77", "2026-07-04 18:00:00")
            ro16_2 = next_knockout_match("90", "73", "75", "2026-07-04 14:00:00")
            ro16_3 = next_knockout_match("91", "76", "78", "2026-07-05 17:00:00")
            ro16_4 = next_knockout_match("92", "79", "80", "2026-07-05 21:00:00")
            ro16_5 = next_knockout_match("93", "83", "84", "2026-07-06 16:00:00")
            ro16_6 = next_knockout_match("94", "81", "82", "2026-07-06 21:00:00")
            ro16_7 = next_knockout_match("95", "86", "88", "2026-07-07 13:00:00")
            ro16_8 = next_knockout_match("96", "85", "87", "2026-07-07 17:00:00")
            new_matches = [ro16_1, ro16_2, ro16_3, ro16_4, ro16_5, ro16_6, ro16_7, ro16_8]
            for match in new_matches:
                logger.info(f"New match in Ro16: {match['home']}-{match['away']}")
        elif int(code) <= 96:
            # Generate QF
            qf_1 = next_knockout_match("97", "89", "90", "2026-07-09 17:00:00")
            qf_2 = next_knockout_match("98", "93", "94", "2026-07-10 16:00:00")
            qf_3 = next_knockout_match("99", "91", "92", "2026-07-11 18:00:00")
            qf_4 = next_knockout_match("100", "95", "96", "2026-07-11 22:00:00")
            new_matches = [qf_1, qf_2, qf_3, qf_4]
            for match in new_matches:
                logger.info(f"New match in QF: {match['home']}-{match['away']}")
        elif int(code) <= 100:
            # Generate SF
            sf_1 = next_knockout_match("101", "97", "98", "2026-07-14 16:00:00")
            sf_2 = next_knockout_match("102", "99", "100", "2026-07-15 16:00:00")
            new_matches = [sf_1, sf_2]
            for match in new_matches:
                logger.info(f"New match in SF: {match['home']}-{match['away']}")
        elif int(code) <= 102:
            # Generate F+3P
            third = next_knockout_match("103", "101", "102", "2026-07-18 18:00:00", loser)
            final = next_knockout_match("104", "101", "102", "2026-07-19 16:00:00")
            new_matches = [third, final]
            logger.info(f"New match in 3P: {third['home']}-{third['away']}")
            logger.info(f"New match in F: {final['home']}-{final['away']}")
        MATCHES_LOCK.acquire()
        MATCHES += new_matches
        MATCHES_LOCK.release()


def new_match(code, home, away, scheduled):
    return {"code": code, "home": home, "away": away, "minutes": 0, "half": 0, "first_half_added_time": None,
            "second_half_added_time": None, "first_overtime_added_time": None, "second_overtime_added_time": None,
            "home_goals": 0, "away_goals": 0, "home_penalties": 0, "away_penalties": 0, "home_yellows": None,
            "away_yellows": None, "home_reds": None, "away_reds": None, "home_odds": 1.00, "draw_odds": 1.00,
            "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current,
            "started_at": datetime.strptime(scheduled, "%Y-%m-%d %H:%M:%S"), "ended_at": None}


def next_knockout_match(code, game1, game2, scheduled, decider=lambda x: winner(x)):
    global MATCHES
    match1 = next((match for match in MATCHES if match["code"] == game1), None)
    match2 = next((match for match in MATCHES if match["code"] == game2), None)
    return new_match(code, decider(match1), decider(match2), scheduled)


def winner(match):
    if match["home_goals"] > match["away_goals"]:
        return match["home"]
    elif match["home_goals"] < match["away_goals"]:
        return match["away"]
    elif match["home_penalties"] > match["away_penalties"]:
        return match["home"]
    else:
        return match["away"]


def loser(match):
    if winner(match) == match["home"]:
        return match["away"]
    else:
        return match["home"]


def calculate_group_standings():
    standings = {}
    for group, group_matches in group_by(MATCHES, "group").items():
        standings_map = {}
        for group_match in group_matches:
            if group_match["home"] not in standings_map:
                standings_map[group_match["home"]] = {"team": group_match["home"], "points": 0, "difference": 0,
                                                      "goals": 0, "conduct": 0, "group": group}

            if group_match["away"] not in standings_map:
                standings_map[group_match["away"]] = {"team": group_match["away"], "points": 0, "difference": 0,
                                                      "goals": 0, "conduct": 0, "group": group}

            standings_map[group_match["home"]]["goals"] += group_match["home_goals"]
            standings_map[group_match["home"]]["conduct"] += -1 * group_match["home_yellows"] - 4 * group_match["home_reds"]
            standings_map[group_match["home"]]["difference"] += (group_match["home_goals"] - group_match["away_goals"])
            standings_map[group_match["away"]]["goals"] += group_match["away_goals"]
            standings_map[group_match["home"]]["conduct"] += -1 * group_match["away_yellows"] - 4 * group_match["away_reds"]
            standings_map[group_match["away"]]["difference"] += (group_match["away_goals"] - group_match["home_goals"])

            if group_match["home_goals"] > group_match["away_goals"]:
                standings_map[group_match["home"]]["points"] += 3
            elif group_match["home_goals"] < group_match["away_goals"]:
                standings_map[group_match["away"]]["points"] += 3
            else:
                standings_map[group_match["away"]]["points"] += 1
                standings_map[group_match["away"]]["points"] += 1

        standings[group] = sorted(list(standings_map.values()),
                                  key=lambda x: (-x["points"], -x["difference"], -x["goals"], -x["conduct"]))

    return standings


def calculate_ro32_combination(standings):
    third_placers = []
    for group, group_standings in standings.items():
        third_placers += [group_standings[2]]
    third_placers = sorted(third_placers, key=lambda x: (-x["points"], -x["difference"], -x["goals"], -x["conduct"]))
    best_third_placers_groups = list(map(lambda x: x["group"], third_placers))[:8]

    import csv
    global COMBINATIONS_CSV

    with open(COMBINATIONS_CSV, newline='', encoding="utf-8") as file:
        reader = csv.reader(file)
        for row in reader:
            if all_in(row, best_third_placers_groups):
                return row

        logger.error(f"Missing combination for Ro32 {best_third_placers_groups}")
        return None


#######################################################################################################################
#                                               SIMULATION FUNCTIONS                                                  #
#######################################################################################################################

def simulate_score(minute, match):
    prob_home, prob_draw, prob_away = odds_to_probabilities(match["home_odds"], match["draw_odds"], match["away_odds"])

    # setting total expected goals
    progress = minute / 90
    average_goals = 3.3
    expected_total_goals = average_goals * progress

    # set each team goal share
    share_a = prob_home / (prob_home + prob_away)
    share_b = prob_away / (prob_home + prob_away)
    lambda_a = expected_total_goals * share_a
    lambda_b = expected_total_goals * share_b

    # calculating goals
    goals_a = poisson(lambda_a)
    goals_b = poisson(lambda_b)
    return int(goals_a), int(goals_b)


def simulate_cards(match):
    home_prob, _, away_prob = odds_to_probabilities(match["home_odds"], match["draw_odds"], match["away_odds"])

    # --- Step 2: match tension ---
    tension = 1.0 - abs(home_prob - away_prob)  # [0,1]

    # --- Step 3: expected yellow cards (total) ---
    base_yellow = 3.2
    extra_yellow = 2.8 * tension
    lambda_total_yellow = base_yellow + extra_yellow

    # --- Step 4: split yellows per team (underdog more aggressive) ---
    home_share = 0.5 + (away_prob - home_prob) * 0.5
    away_share = 1.0 - home_share

    lambda_home_yellows = max(0.1, lambda_total_yellow * home_share)
    lambda_away_yellows = max(0.1, lambda_total_yellow * away_share)

    # --- Step 5: sample yellow cards ---
    home_yellows = poisson(lambda_home_yellows)
    away_yellows = poisson(lambda_away_yellows)

    # --- Step 6: red cards ---
    # Base chance + tension + yellows influence
    def red_cards(yellow, prob_diff):
        base_red_prob = 0.04  # baseline chance
        tension_bonus = 0.05 * tension
        yellow_factor = 0.03 * yellow  # more yellows → higher red chance

        p_red = base_red_prob + tension_bonus + yellow_factor + prob_diff * 0.05
        p_red = min(p_red, 0.6)  # cap to avoid insanity

        # allow 0, 1, or rarely 2 reds
        reds = 0
        if random.random() < p_red:
            reds = 1
            if random.random() < (p_red * 0.15):
                reds += 1
        return reds

    # underdog slightly more likely to get reds
    home_reds = red_cards(home_yellows, away_prob - home_prob)
    away_reds = red_cards(away_yellows, home_prob - away_prob)

    return int(home_yellows), int(away_yellows), int(home_reds), int(away_reds)


def simulate_added(half):
    if half == 1:
        mean = 2.5
        std = 1.0
    elif half == 2:
        mean = 4.5
        std = 1.8
    elif half == 3:
        mean = 0.5
        std = 0.9
    elif half == 4:
        mean = 1.5
        std = 2.2
    else:
        mean = 1.0
        std = 2.0

    value = random.gauss(mean, std)
    value = max(0, min(value, 12))
    return int(round(value))


def simulate_penalties():
    # Valid early finishes (before 5 full rounds)
    early_results = [(2, 0), (3, 0), (3, 1), (4, 1), (4, 2)]

    # Decide if it's early finish or full shootout
    if random.random() < 0.35:  # tweak probability if you want
        home, away = random.choice(early_results)
    else:
        # Full shootout → must end with difference of 1
        base = 5

        # maybe extend into sudden death
        extra_rounds = random.randint(0, 5)  # allows 5-4 up to 10-9
        home = base + extra_rounds
        away = base + extra_rounds - 1

    # Randomly assign winner (swap scores)
    if random.random() < 0.5:
        home, away = away, home

    return home, away


#######################################################################################################################
#                                                   UTILITY FUNCTIONS                                                 #
#######################################################################################################################

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


def diff_minutes(date1, date2):
    return int((date1 - date2).total_seconds() / 60)


def diff_hours(date1, date2):
    return int(diff_minutes(date1, date2) / 60)


def or_zero(number):
    return number if number is not None else 0


def group_by(array, key):
    grouped = {}
    for item in array:
        if item[key] not in grouped:
            grouped[item[key]] = []
        grouped[item[key]] += [item]
    return grouped


def all_in(target, elements):
    for element in elements:
        if element not in target:
            return False
    return True


#######################################################################################################################
#                                                  RUNNING SIMULATION                                                 #
#######################################################################################################################

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

                    over90 = False
                    new_half, new_minutes, new_ended_at = None, None, None
                    home_yellows, away_yellows, home_reds, away_reds = None, None, None, None
                    new_home_penalties, new_away_penalties = None, None
                    new_added_time1, new_added_time2 = match["first_half_added_time"], match["second_half_added_time"]
                    new_added_time3, new_added_time4 = match["first_overtime_added_time"], match["second_overtime_added_time"]
                    if minutes < 45:
                        # First half
                        new_half = 1
                        new_minutes = minutes
                    else:
                        if not new_added_time1:
                            new_added_time1 = simulate_added(half=1)
                            logger.debug(f'Added time for first half: {new_added_time1}\'')
                        if minutes < 50 + new_added_time1:
                            # First half (added time)
                            new_half = 1
                            new_minutes = 45 + new_added_time1
                        elif minutes < 65 + new_added_time1:
                            # Half-time
                            new_half = 1
                            new_minutes = 45 + new_added_time1
                            new_status = "HALF_TIME"
                        elif minutes < 110 + new_added_time1:
                            # Second half
                            new_half = 2
                            new_minutes = minutes - (20 + new_added_time1)
                        else:
                            # Second half (added time)
                            over90 = True
                            new_half = 2
                            new_added_time2 = simulate_added(half=2)
                            logger.debug(f'Added time for second half: {new_added_time1}\'')
                            new_minutes = 90 + new_added_time2

                    home_goals, away_goals = simulate_score(new_minutes + or_zero(new_added_time1) + or_zero(new_added_time2), match)
                    new_home_goals = max(match["home_goals"], home_goals)
                    new_away_goals = max(match["away_goals"], away_goals)

                    if over90:
                        home_yellows, away_yellows, home_reds, away_reds = simulate_cards(match)
                        if int(match["code"]) <= 72:
                            match_ended = True
                            new_status = "COMPLETED"
                        elif new_home_goals != new_away_goals:
                            match_ended = True
                            new_status = "COMPLETED"
                        else:
                            # Half-time
                            if minutes < 115 + new_added_time1 + new_added_time2:
                                new_half = 2
                                new_minutes = 90 + new_added_time2
                                new_status = "HALF_TIME"
                            else:
                                if not new_added_time3:
                                    new_added_time3 = simulate_added(half=3)
                                    logger.debug(f'Added time for first extra time: {new_added_time3}\'')
                                if minutes < 130 + new_added_time1 + new_added_time2 + new_added_time3:
                                    new_half = 3
                                    new_minutes = minutes - (30 + new_added_time1 + new_added_time2)
                                    new_status = "IN_PLAY"
                                elif minutes < 135 + new_added_time1 + new_added_time2 + new_added_time3:
                                    new_half = 3
                                    new_minutes = 105 + new_added_time3
                                    new_status = "HALF_TIME"
                                else:
                                    if not new_added_time4:
                                        new_added_time4 = simulate_added(half=4)
                                        logger.debug(f'Added time for second extra time: {new_added_time4}\'')
                                    if minutes < 150 + new_added_time1 + new_added_time2 + new_added_time3 + new_added_time4:
                                        new_half = 4
                                        new_minutes = minutes - (35 + new_added_time1 + new_added_time2 + new_added_time3)
                                        new_status = "IN_PLAY"
                                    else:
                                        match_ended = True
                                        new_status = "COMPLETED"
                                        new_minutes = 120 + new_added_time4
                                        extra_home_yellows, extra_away_yellows, extra_home_red, extra_away_red = simulate_cards(match)
                                        home_yellows += int(extra_home_yellows / 3)
                                        away_yellows += int(extra_away_yellows / 3)
                                        home_reds += int(extra_home_red / 3)
                                        away_reds += int(extra_away_red / 3)

                            home_extra_goals, away_extra_goals = simulate_score(new_minutes - 90 + or_zero(new_added_time3) + or_zero(new_added_time4), match)
                            new_home_goals += home_extra_goals
                            new_away_goals += away_extra_goals

                            if match_ended and new_home_goals == new_away_goals:
                                new_home_penalties, new_away_penalties = simulate_penalties()
                                if minutes < 150 + new_added_time1 + new_added_time2 + new_added_time3 + new_added_time4:
                                    new_status = "PENALTIES"

                    logger.debug(f'Updated match: ({new_status}) {match["home"]} {new_home_goals}-{new_away_goals} {match["away"]}, {new_minutes}\' ({new_half}H)')
                    if match_ended:
                        logger.info(f'Match ended: {match["home"]} {new_home_goals}-{new_away_goals} {match["away"]} [{or_zero(home_yellows)}A+{or_zero(home_reds)}R|{or_zero(away_yellows)}A+{or_zero(away_reds)}R]')
                        new_ended_at = match["started_at"] + timedelta(minutes=(115 + new_added_time1 + new_added_time2))

                    MATCHES_LOCK.acquire()
                    match["status"] = new_status
                    match["home_goals"] = int(new_home_goals)
                    match["away_goals"] = int(new_away_goals)
                    match["half"] = int(new_half)
                    match["minutes"] = int(new_minutes)
                    match["first_half_added_time"] = new_added_time1
                    match["second_half_added_time"] = new_added_time2
                    match["first_overtime_added_time"] = new_added_time3
                    match["second_overtime_added_time"] = new_added_time4
                    match["home_yellows"] = home_yellows
                    match["away_yellows"] = away_yellows
                    match["home_reds"] = home_reds
                    match["away_reds"] = away_reds
                    match["ended_at"] = new_ended_at
                    match["home_penalties"] = new_home_penalties
                    match["away_penalties"] = new_away_penalties
                    MATCHES_LOCK.release()

                    if match_ended:
                        check_round(match)

                else:
                    # Not yet played
                    if not match["odds_calculated_at"] or diff_hours(current, match["odds_calculated_at"]) >= 24:
                        match["home_odds"] = round(match["home_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["draw_odds"] = round(match["draw_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["away_odds"] = round(match["away_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["odds_calculated_at"] = current


#######################################################################################################################
#                                                       SERVER                                                        #
#######################################################################################################################

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

    def _process_update_match(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        code = body.get("code")
        if not code:
            return 400, {"error": "'code' is required"}
        try:
            global MATCHES
            MATCHES_LOCK.acquire()
            for match in MATCHES:
                if match["code"] == code:
                    for field in body:
                        match[field] = body.get(field)
                    break
            MATCHES_LOCK.release()
            logger.info(f"Match updated: {code}")
            return 200, None
        except ValueError:
            return 400, {"error": "Invalid number format"}

    @staticmethod
    def serialize_match(match):
        match_copy = match.copy()
        for field in ["started_at", "ended_at", "odds_changed_at"]:
            value = match_copy.get(field)
            if value is not None:
                match_copy[field] = value.isoformat() + "-03:00"
        return match_copy

    def _send_matches(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        TIME_LOCK.acquire()
        MATCHES_LOCK.acquire()
        self.wfile.write(json.dumps({
            "current": current,
            "matches": [self.serialize_match(match) for match in MATCHES]
        }, default=json_serializer).encode())
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
        if urlparse(self.path).path == "/matches":
            self._send_matches()
        else:
            self._reject()

    def do_PUT(self):
        if urlparse(self.path).path == "/pause":
            status_code = self._accept_pause()
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
