package Smart_Choice_Cloring.Service;

import Smart_Choice_Cloring.Entity.Subsidy;
import Smart_Choice_Cloring.Repository.SubsidyRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class SubsidyService {

    private final SubsidyRepository subsidyRepository;
    private final String URL = "https://m.smartchoice.or.kr/smc/mobile/dantongList.do?type=m";

    @Transactional
    public void crawlAndSaveSubsidies() {
        try {
            // 1. 해당 URL의 HTML 문서 가져오기
            Document doc = Jsoup.connect(URL).get();

            // 2. 데이터가 포함된 테이블의 행(tr) 선택 (사이트 구조에 따라 셀렉터 수정 필요)
            // 스마트초이스 모바일 페이지의 리스트 항목을 타겟팅합니다.
            Elements rows = doc.select(".list_type01 li");

            for (Element row : rows) {
                // 각 항목에서 통신사, 기기명, 지원금액 등을 추출
                String telecom = row.select(".telecom").text();
                String device = row.select(".device_name").text();
                String plan = row.select(".plan").text();
                String amount = row.select(".price").text();

                // 3. 엔티티 생성 및 DB 저장
                Subsidy subsidy = Subsidy.builder()
                        .telecom(telecom)
                        .deviceName(device)
                        .planName(plan)
                        .supportAmount(amount)
                        .build();

                subsidyRepository.save(subsidy);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("크롤링 중 오류가 발생했습니다.");
        }
    }
}