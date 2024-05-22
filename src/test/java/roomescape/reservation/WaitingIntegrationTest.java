package roomescape.reservation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import roomescape.auth.dto.request.LoginRequest;
import roomescape.fixture.MemberFixture;
import roomescape.member.domain.Member;
import roomescape.member.domain.Role;
import roomescape.member.repository.MemberRepository;
import roomescape.reservation.model.Reservation;
import roomescape.reservation.model.ReservationTime;
import roomescape.reservation.model.Theme;
import roomescape.reservation.model.Waiting;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.reservation.repository.ReservationTimeRepository;
import roomescape.reservation.repository.ThemeRepository;
import roomescape.reservation.repository.WaitingRepository;
import roomescape.util.IntegrationTest;

@IntegrationTest
class WaitingIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ReservationTimeRepository reservationTimeRepository;

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private WaitingRepository waitingRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void init() {
        RestAssured.port = this.port;
    }

    private String getTokenByLogin(Member member) {
        return RestAssured
                .given().log().all()
                .body(new LoginRequest(member.getEmail().email(), member.getPassword()))
                .contentType(ContentType.JSON)
                .when().post("/login")
                .then().log().cookies().extract().cookie("token");
    }

    private void saveTimeThemeMemberForReservation() {
        memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));
        reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
    }

    @Test
    @DisplayName("방탈출 예약 대기 생성 성공 시, 생성된 시간대의 정보를 반환한다.")
    void createWaiting() {
        ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        Theme theme = themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
        Member oneMember = memberRepository.save(MemberFixture.getOne());
        reservationRepository.save(new Reservation(oneMember, LocalDate.parse("2024-11-23"), reservationTime, theme));
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@login.com", "hihi"));

        Map<String, Object> params = new HashMap<>();
        params.put("date", "2024-11-23");
        params.put("timeId", reservationTime.getId());
        params.put("themeId", theme.getId());

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", getTokenByLogin(member))
                .body(params)
                .when().post("/waitings")
                .then().log().all()

                .statusCode(201)
                .body("id", equalTo(1))
                .body("member.name", equalTo("몰리"))
                .body("theme.name", equalTo("테마이름"))
                .body("date", equalTo("2024-11-23"))
                .body("time.startAt", equalTo("20:00"));
    }

    @Test
    @DisplayName("방탈출 예약 대기 생성 시, 날짜가 과거인 경우 예외를 반환한다.")
    void createWaitingWhenTimeIsPast() {
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));
        Map<String, Object> params = new HashMap<>();
        params.put("date", "2000-11-30");
        params.put("timeId", 1);
        params.put("themeId", 1);

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", getTokenByLogin(member))
                .body(params)
                .when().post("/waitings")
                .then().log().all()

                .statusCode(400)
                .body("detail", equalTo("예약 대기 날짜는 현재보다 과거일 수 없습니다."));
    }

    @Test
    @DisplayName("예약 대기 생성 시 같은 테마, 같은 날짜, 같은 시간에 예약과 예약 대기가 없는 경우 예외를 반환한다.")
    void createWaitingWhenTimeAndDateAndThemeNotExist() {
        ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        Theme theme = themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));

        Map<String, Object> params = new HashMap<>();
        params.put("date", "2024-11-23");
        params.put("timeId", reservationTime.getId());
        params.put("themeId", theme.getId());

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", getTokenByLogin(member))
                .body(params)
                .when().post("/waitings")
                .then().log().all()

                .statusCode(400)
                .body("detail", equalTo("2024-11-23 20:00의 테마이름 테마는 바로 예약 가능하여 대기가 불가능합니다."));
    }

    @Test
    @DisplayName("예약 대기 생성 시 같은 테마, 같은 날짜, 같은 시간에 내 예약이 있는 경우 예외를 반환한다.")
    void createReservationWhenAlreadyHasReservation() {
        ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        Theme theme = themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));
        reservationRepository.save(new Reservation(member, LocalDate.parse("2024-11-23"), reservationTime, theme));

        Map<String, Object> params = new HashMap<>();
        params.put("date", "2024-11-23");
        params.put("timeId", reservationTime.getId());
        params.put("themeId", theme.getId());

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", getTokenByLogin(member))
                .body(params)
                .when().post("/waitings")
                .then().log().all()

                .statusCode(400)
                .body("detail", equalTo("이미 본인의 예약이 존재하여 대기를 생성할 수 없습니다."));
    }

    @Test
    @DisplayName("예약 대기 생성 시 같은 테마, 같은 날짜, 같은 시간에 내 예약 대기가 있는 경우 예외를 반환한다.")
    void createReservationWhenAlreadyHasWaiting() {
        ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        Theme theme = themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
        Member oneMember = memberRepository.save(MemberFixture.getOne());
        reservationRepository.save(
                new Reservation(oneMember, LocalDate.parse("2024-11-23"), reservationTime, theme));
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));
        waitingRepository.save(new Waiting(member, LocalDate.parse("2024-11-23"), reservationTime, theme));

        Map<String, Object> params = new HashMap<>();
        params.put("date", "2024-11-23");
        params.put("timeId", reservationTime.getId());
        params.put("themeId", theme.getId());

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", getTokenByLogin(member))
                .body(params)
                .when().post("/waitings")
                .then().log().all()

                .statusCode(400)
                .body("detail", equalTo("이미 본인의 대기가 존재하여 대기를 생성할 수 없습니다."));
    }

    @Test
    @DisplayName("방탈출 예약 목록을 조회한다.")
    void getReservationTimes() {
        ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("20:00")));
        Theme theme = themeRepository.save(new Theme("테마이름", "설명", "썸네일"));
        Member oneMember = memberRepository.save(MemberFixture.getOne());
        reservationRepository.save(
                new Reservation(oneMember, LocalDate.parse("2024-11-23"), reservationTime, theme));
        Member member = memberRepository.save(new Member("몰리", Role.USER, "login@naver.com", "hihi"));
        waitingRepository.save(new Waiting(member, LocalDate.parse("2024-11-23"), reservationTime, theme));
        Member member2 = memberRepository.save(new Member("로키", Role.USER, "loki@naver.com", "hihi"));
        waitingRepository.save(new Waiting(member2, LocalDate.parse("2024-11-23"), reservationTime, theme));

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .when().get("/waitings")
                .then().log().all()

                .statusCode(200)
                .body("id", hasItems(1, 2))
                .body("size()", equalTo(2));
    }
//
//    @Test
//    @DisplayName("방탈출 예약 하나를 조회한다.")
//    void getReservationTime() {
//        saveTimeThemeMemberForReservation();
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-11-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-12-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//
//        RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().get("/reservations/1")
//                .then().log().all()
//
//                .statusCode(200)
//                .body("id", equalTo(1))
//                .body("member.name", equalTo("몰리"))
//                .body("date", equalTo("2024-11-23"));
//    }
//
//    @Test
//    @DisplayName("방탈출 예약 조회 시, 조회하려는 예약이 없는 경우 예외를 반환한다.")
//    void getReservationTime_WhenTimeNotExist() {
//        RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().get("/reservations/1")
//                .then().log().all()
//
//                .statusCode(404)
//                .body("detail", equalTo("식별자 1에 해당하는 예약이 존재하지 않아 예약을 조회할 수 없습니다."));
//    }
//
//    @Test
//    @DisplayName("해당 날짜와 테마를 통해 예약 가능한 시간 조회한다.")
//    void getAvailableTimes() {
//        saveTimeThemeMemberForReservation();
//        reservationTimeRepository.save(new ReservationTime(LocalTime.parse("10:00")));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-11-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-12-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//
//        List<FindAvailableTimesResponse> findAvailableTimesResponses = RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().get("/reservations/times?date=2024-11-23&themeId=1")
//                .then().log().all()
//                .extract().jsonPath()
//                .getList(".", FindAvailableTimesResponse.class);
//
//        assertAll(
//                () -> assertThat(findAvailableTimesResponses).hasSize(2),
//                () -> assertThat(findAvailableTimesResponses).containsExactlyInAnyOrder(
//                        new FindAvailableTimesResponse(1L, LocalTime.parse("20:00"), true),
//                        new FindAvailableTimesResponse(2L, LocalTime.parse("10:00"), false)
//                )
//        );
//    }
//
//    @Test
//    @DisplayName("해당 날짜와 테마, 기간에 해당하는 예약을 검색한다.")
//    void searchBy() {
//        saveTimeThemeMemberForReservation();
//        reservationTimeRepository.save(new ReservationTime(LocalTime.parse("10:00")));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-11-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-12-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2025-01-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//
//        RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().get("/reservations/search?memberId=1&themeId=1&dateFrom=2024-12-23&dateTo=2025-01-23")
//                .then().log().all()
//
//                .statusCode(200)
//                .body("[0].id", equalTo(2))
//                .body("[0].date", equalTo("2024-12-23"))
//
//                .body("[1].id", equalTo(3))
//                .body("[1].date", equalTo("2025-01-23"));
//    }
//
//    @Test
//    @DisplayName("방탈출 예약 하나를 삭제한다.")
//    void deleteReservationTime() {
//        saveTimeThemeMemberForReservation();
//        reservationRepository.save(new Reservation(memberRepository.getById(1L), LocalDate.parse("2024-11-23"),
//                reservationTimeRepository.getById(1L), themeRepository.getById(1L)));
//
//        RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().delete("/reservations/1")
//                .then().log().all()
//
//                .statusCode(204);
//    }
//
//    @Test
//    @DisplayName("방탈출 예약 조회 시, 조회하려는 예약이 없는 경우 예외를 반환한다.")
//    void deleteReservationTime_WhenTimeNotExist() {
//        RestAssured.given().log().all()
//                .contentType(ContentType.JSON)
//                .when().delete("/reservations/1")
//                .then().log().all()
//
//                .statusCode(404)
//                .body("detail", equalTo("식별자 1에 해당하는 예약이 존재하지 않습니다. 삭제가 불가능합니다."));
//    }
}
