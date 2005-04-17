<%@ page session="false" %>
<%@ taglib uri="http://java.sun.com/jstl/core" prefix="c" %>

<HTML>
	<BODY>
		<DIV align="left">The Number Guess Game: Guess a number between 1 and 100!</DIV>
		<HR>
		<DIV align="left">
			Number of guesses so far: <c:out value="${data.guesses}" default="0"/><BR>
			Last guess result: <c:out value="${data.lastGuessResult}"/><BR>
			
			<FORM name="guessForm" method="post">
				<INPUT type="hidden" name="_flowExecutionId" value="<c:out value="${flowExecutionId}"/>">
				<INPUT type="hidden" name="_eventId" value="submit">
				<table>
				    <tr><td>Guess:</td><td><INPUT type="text" name="guess" value="<c:out value="${param.guess}"/>"/></td></tr>
				</table>
			</FORM>
		</DIV>
		<HR>
		<DIV align="right">
			<INPUT type="button" onclick="javascript:document.guessForm.submit()" value="Guess">
		</DIV>
	</BODY>
</HTML>