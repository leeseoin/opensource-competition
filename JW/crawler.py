import requests
from bs4 import BeautifulSoup
import json
import os
from openai import OpenAI # 실제 사용 시 주석 해제

# API 키 세팅 (터미널 환경 변수에서 가져오는 것을 권장)
# client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))

def fetch_html(url):
    """1단계: 대상 쇼핑몰에 접속해서 HTML을 긁어옵니다."""
    print(f"🔗 접속 시도 중: {url}")
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    }
    response = requests.get(url, headers=headers)

    if response.status_code == 200:
        print("✅ HTML 원본 수집 완료")
        return response.text
    else:
        print(f"❌ 접속 실패: 에러 코드 {response.status_code}")
        return None

def extract_text_with_bs4(html_content):
    """2단계: 불필요한 태그를 날리고 순수 텍스트만 추출합니다."""
    print("✂️ BeautifulSoup으로 데이터 경량화 중...")
    soup = BeautifulSoup(html_content, 'html.parser')

    # script, style 등 불필요한 요소 제거
    for script in soup(["script", "style", "nav", "footer"]):
        script.extract()

    text = soup.get_text(separator=' ', strip=True)
    # 토큰 절약을 위해 앞부분 일부 텍스트만 자르기 (실제로는 원하는 구역을 쪼개서 보냅니다)
    return text[:2000]

def parse_with_ai(raw_text):
    """3단계: 추출된 텍스트를 AI에게 전달하여 JSON으로 정제합니다."""
    print("🧠 AI 에이전트에게 데이터 정제 요청 중...")

    prompt = f"""
    다음 텍스트는 쇼핑몰 상품 페이지의 일부야.
    여기서 '상품명', '가격(숫자만)', '품절여부(true/false)', '평점'을 찾아서 
    반드시 JSON 형식으로만 응답해줘.
    
    [원본 텍스트]
    {raw_text}
    """

    # --- 아래는 실제 OpenAI API 호출 부분입니다 (현재는 주석 처리) ---
    # response = client.chat.completions.create(
    #     model="gpt-3.5-turbo",
    #     messages=[{"role": "user", "content": prompt}],
    #     response_format={ "type": "json_object" }
    # )
    # return json.loads(response.choices[0].message.content)

    # 시뮬레이션을 위한 가짜 반환값 (실제 연동 전 테스트용)
    mock_result = {
        "상품명": "비즈니스 출퇴근용 방수 백팩",
        "가격": 89000,
        "품절여부": False,
        "평점": 4.8
    }
    return mock_result

# --- 실행 부분 ---
if __name__ == "__main__":
    target_url = "https://example.com/product/12345" # 테스트할 상품 URL

    html = fetch_html(target_url)
    if html:
        clean_text = extract_text_with_bs4(html)
        final_data = parse_with_ai(clean_text)

        print("\n🎉 [최종 AI 파싱 결과]")
        print(json.dumps(final_data, indent=4, ensure_ascii=False))