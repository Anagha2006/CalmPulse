import os
import pickle
import pandas as pd
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# -----------------------------
# MODEL CONFIGURATIONS & FEATURE ORDERS
# -----------------------------
EMPLOYEE_OHE_COLS = [
    'job_role_Data Scientist',
    'job_role_Designer',
    'job_role_HR',
    'job_role_Marketing',
    'job_role_Project Manager',
    'job_role_Sales',
    'job_role_Software Engineer'
]

EMPLOYEE_FEATURES_ORDER = [
    'working_hours',
    'virtual_meetings',
    'work_life_balance',
    'access_to_mental_health',
    'satisfaction_with_remote_work',
    'company_support',
    'physical_activity',
    'sleep_quality',
    'meetings_per_hour'
] + EMPLOYEE_OHE_COLS

STUDENT_FEATURES_ORDER = [
    'anxiety_level',
    'depression',
    'sleep_quality',
    'academic_performance',
    'study_load',
    'teacher_student_relationship',
    'future_career_concerns',
    'social_support',
    'peer_pressure',
    'extracurricular_activities'
]

# -----------------------------
# LOAD MODELS ONCE AT STARTUP
# -----------------------------
employee_model = None
student_model = None

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

def find_model_path(filename):
    """Finds the model file searching multiple potential locations."""
    candidates = [
        os.path.join(BASE_DIR, 'Model', filename),
        os.path.join(BASE_DIR, filename),
        os.path.join(os.getcwd(), 'ML', 'Model', filename),
        os.path.join(os.getcwd(), 'Model', filename),
        os.path.join(os.getcwd(), filename),
    ]
    for path in candidates:
        if os.path.exists(path):
            return path
    return None

emp_path = find_model_path('employee_model.pkl')
if emp_path:
    try:
        with open(emp_path, 'rb') as f:
            employee_model = pickle.load(f)
        print(f"Employee ML model loaded successfully from: {emp_path}")
    except Exception as e:
        print(f"Error loading employee model from {emp_path}: {e}")
else:
    print("Warning: employee_model.pkl not found in any standard path.")

stud_path = find_model_path('student_model.pkl')
if stud_path:
    try:
        with open(stud_path, 'rb') as f:
            student_model = pickle.load(f)
        print(f"Student ML model loaded successfully from: {stud_path}")
    except Exception as e:
        print(f"Error loading student model from {stud_path}: {e}")
else:
    print("Warning: student_model.pkl not found in any standard path.")


# -----------------------------
# PREPROCESSING FUNCTIONS
# -----------------------------
def preprocess_employee_data_for_prediction(df):
    """
    Cleans and aligns employee metrics to match the exact 16-feature schema:
    [working_hours, virtual_meetings, work_life_balance, access_to_mental_health,
     satisfaction_with_remote_work, company_support, physical_activity,
     sleep_quality, meetings_per_hour, ...7 OHE job roles]
    """
    row = df.iloc[0].to_dict()

    # 1. Job Role normalization
    job_role_raw = str(row.get('job_role', '') or '').strip()
    norm_role = job_role_raw.lower().replace('_', ' ')

    # 2. Categorical mappings (case-insensitive & numeric-tolerant)
    def parse_access_to_mental_health(val):
        s = str(val).strip().lower()
        if s in ['yes', '1', 'true']:
            return 1
        return 0

    def parse_satisfaction(val):
        s = str(val).strip().lower()
        if s in ['satisfied', '1', 'true', 'yes']:
            return 1
        return 0

    def parse_physical_activity(val):
        s = str(val).strip().lower()
        mapping = {'none': 0, 'weekly': 1, 'daily': 2, '0': 0, '1': 1, '2': 2}
        return mapping.get(s, 0)

    def parse_sleep_quality(val):
        s = str(val).strip().lower()
        mapping = {'poor': 1, 'average': 2, 'good': 3, '1': 1, '2': 2, '3': 3}
        return mapping.get(s, 2)

    def to_float(val, default=0.0):
        try:
            if val is None or pd.isna(val):
                return default
            return float(val)
        except (ValueError, TypeError):
            return default

    working_hours = to_float(row.get('working_hours'), 40.0)
    virtual_meetings = to_float(row.get('virtual_meetings'), 3.0)
    work_life_balance = to_float(row.get('work_life_balance'), 3.0)
    company_support = to_float(row.get('company_support'), 3.0)

    access_to_mental_health = parse_access_to_mental_health(row.get('access_to_mental_health'))
    satisfaction_with_remote_work = parse_satisfaction(row.get('satisfaction_with_remote_work'))
    physical_activity = parse_physical_activity(row.get('physical_activity'))
    sleep_quality = parse_sleep_quality(row.get('sleep_quality'))

    meetings_per_hour = (virtual_meetings / working_hours) if working_hours > 0 else 0.0
    if np.isnan(meetings_per_hour) or np.isinf(meetings_per_hour):
        meetings_per_hour = 0.0

    # Build record dictionary
    processed = {
        'working_hours': working_hours,
        'virtual_meetings': virtual_meetings,
        'work_life_balance': work_life_balance,
        'access_to_mental_health': access_to_mental_health,
        'satisfaction_with_remote_work': satisfaction_with_remote_work,
        'company_support': company_support,
        'physical_activity': physical_activity,
        'sleep_quality': sleep_quality,
        'meetings_per_hour': meetings_per_hour
    }

    # One-Hot Encode Job Roles with exact column names (including spaces)
    for col in EMPLOYEE_OHE_COLS:
        col_role = col.replace('job_role_', '').strip().lower().replace('_', ' ')
        processed[col] = 1 if norm_role == col_role else 0

    out_df = pd.DataFrame([processed])[EMPLOYEE_FEATURES_ORDER]
    return out_df


def preprocess_student_data_for_prediction(df):
    """
    Cleans and aligns student survey metrics to match the exact 10-feature schema:
    [anxiety_level, depression, sleep_quality, academic_performance,
     study_load, teacher_student_relationship, future_career_concerns,
     social_support, peer_pressure, extracurricular_activities]
    """
    row = df.iloc[0].to_dict()

    def to_float(val, default=0.0):
        try:
            if val is None or pd.isna(val):
                return default
            return float(val)
        except (ValueError, TypeError):
            return default

    def parse_student_sleep(val):
        if val is None or pd.isna(val):
            return 3.0
        s = str(val).strip().lower()
        text_mapping = {'poor': 1.0, 'average': 3.0, 'good': 5.0}
        if s in text_mapping:
            return text_mapping[s]
        try:
            return float(s)
        except (ValueError, TypeError):
            return 3.0

    # Extracurricular might be extracurricular_load or extracurricular_activities
    ec = row.get('extracurricular_activities')
    if ec is None or pd.isna(ec):
        ec = row.get('extracurricular_load', 3.0)

    processed = {
        'anxiety_level': to_float(row.get('anxiety_level'), 0.0),
        'depression': to_float(row.get('depression'), 0.0),
        'sleep_quality': parse_student_sleep(row.get('sleep_quality')),
        'academic_performance': to_float(row.get('academic_performance'), 3.0),
        'study_load': to_float(row.get('study_load'), 3.0),
        'teacher_student_relationship': to_float(row.get('teacher_student_relationship'), 3.0),
        'future_career_concerns': to_float(row.get('future_career_concerns'), 3.0),
        'social_support': to_float(row.get('social_support'), 3.0),
        'peer_pressure': to_float(row.get('peer_pressure'), 3.0),
        'extracurricular_activities': to_float(ec, 3.0)
    }

    out_df = pd.DataFrame([processed])[STUDENT_FEATURES_ORDER]
    return out_df


# -----------------------------
# ROOT & HEALTH ENDPOINTS
# -----------------------------
@app.route('/', methods=['GET'])
def home():
    return jsonify({
        "status": "running",
        "service": "CalmPulse ML Prediction API",
        "message": "API is running successfully",
        "employee_model_loaded": employee_model is not None,
        "student_model_loaded": student_model is not None
    })

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        "status": "healthy",
        "employee_model_loaded": employee_model is not None,
        "student_model_loaded": student_model is not None
    })

# -----------------------------
# PREDICT API ENDPOINT
# -----------------------------
@app.route('/predict_ml', methods=['POST'])
def predict_ml():
    body = request.get_json(silent=True)
    if not body:
        return jsonify({"error": "Missing JSON request body"}), 400

    role = body.get('role')
    data = body.get('data')

    if not role or data is None:
        return jsonify({"error": "Missing 'role' or 'data' in request body"}), 400

    role_clean = str(role).strip().lower()

    try:
        df = pd.DataFrame([data])
        
        if role_clean == 'employee':
            if employee_model is None:
                return jsonify({"error": "Employee model is not loaded on server."}), 500
            
            df_prep = preprocess_employee_data_for_prediction(df)
            prediction = employee_model.predict(df_prep)
            
        elif role_clean == 'student':
            if student_model is None:
                return jsonify({"error": "Student model is not loaded on server."}), 500
            
            df_prep = preprocess_student_data_for_prediction(df)
            prediction = student_model.predict(df_prep)
            
        else:
            return jsonify({"error": f"Invalid role '{role}'. Must be 'employee' or 'student'."}), 400

        stress_score = float(prediction[0])
        return jsonify({
            "stress_score": stress_score,
            "role": role_clean
        })

    except Exception as e:
        app.logger.error(f"ML Microservice Error: {e}", exc_info=True)
        return jsonify({"error": f"ML processing error: {str(e)}"}), 500


if __name__ == '__main__':
    port = int(os.environ.get("PORT", 5001))
    app.run(host='0.0.0.0', port=port)
