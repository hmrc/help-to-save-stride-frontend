/*
    For cases whereby a page has both a submit and a cancel button, and the cancel button is an anchor link.
    To prevent the user from clicking submit, and then clicking cancel.

    <a data-button="submit-disabled"> ...
*/

;(function preventSubmitAfterCancel() {
  var disableOnSubmit = document.querySelectorAll('[data-button=submit-disabled]')[0]

  // On cancel click, disable the cancel & submit buttons
  $(disableOnSubmit).on('click', function() {
    $(this).attr('disabled', true)
    $('input[type=submit], button[type=submit]').prop('disabled', true);
  })
})();


;(function preventCancelAfterSubmit() {
  var disableOnSubmit = document.querySelectorAll('[data-button=submit-disabled]')[0]

  /**
  * On form submit, prevent cancel button click.
  * A module within ASSETS_FRONTEND prevents the double click of the submit button:
  * https://github.com/hmrc/assets-frontend/blob/master/assets/javascripts/modules/preventDoubleSubmit.js
  */

  $('form').on('submit', function () {
    $(disableOnSubmit).attr('disabled', true)
  })
})();



